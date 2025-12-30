package io.github.demiourgoi.linoleum

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.core.fs.Path
import org.apache.flink.api.common.serialization.SimpleStringEncoder
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy

import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions.checkNotNull

import scala.collection.convert.ImplicitConversions.`iterator asScala`
import scala.math.pow
import source.SpanInfoStream

import com.google.protobuf.ByteString
import java.time.Duration
import java.{lang => jlang, util => jutil}
import java.time.Instant
import com.google.protobuf.Timestamp
import org.bson.BsonDocument

import io.opentelemetry.proto.common.v1.{AnyValue, KeyValue}
import io.opentelemetry.proto.trace.v1.Span

import io.github.demiourgoi.sscheck.prop.tl.{
  Formula,
  NextFormula,
  Time => SscheckTime
}
import es.ucm.maude.bindings.{
  maude => jMaude,
  MaudeRuntime,
  Module => MaudeModule
}

import messages.{LinoleumEvent, SpanInfo}
import evaluator.SpanStreamEvaluatorParams

object Linoleum {
  import config._
  import source._
  import sink._
  import evaluator._
  import formulas._
  import maude._

  private val log = LoggerFactory.getLogger(Linoleum.getClass.getName)

  // FIXME: complete Maude monitor property type class instance, evaluate
  // refactor for single execute method
  // def execute(cfg: LinoleumConfig, maudeJob: MaudeJob): Unit = {
  //   // TODO: this is a poor abstraction (stdlib loading not considered for example). But make this
  //   // easy so the user focuses on the Maude code: e.g. require an entry point module
  //   // maudeJob.programPaths.foreach { MaudeRuntime.loadFromResources(_) }
  //   // val maudeModules = maudeJob.maudeModules.map { jMaude.getModule(_) }
  // }

  def execute(cfg: LinoleumConfig, formula: LinoleumFormula): Unit = {
    setupFlinkJob(cfg, formula).execute(cfg.jobName)
  }

  private[linoleum] def setupFlinkJob(
      linolenumCfg: LinoleumConfig,
      formula: LinoleumFormula
  ): StreamExecutionEnvironment = {
    val env = LinoleumSrc.flinkEnv(linolenumCfg)
    val linoleumSrc = new LinoleumSrc(linolenumCfg)
    val spanInfos = linoleumSrc(env)

    if (linolenumCfg.sink.logMaudeTerms) {
      if (!linolenumCfg.localFlinkEnv) {
        log.warn(
          "Logging traces as Maude terms is only supported for local env executions, skipping collection"
        )
      } else {
        logMaudeTerms(spanInfos)
      }
    }

    import PropertySyntax.PropertyOps
    import PropertyInstances.formulaProperty
    val spamEvaluator = new SpanStreamEvaluator(
      formula.streamEvaluatorParams
    )
    val evaluatedSpans = spamEvaluator(spanInfos)

    evaluatedSpans.print()

    val linoleumSink = new LinoleumSink(linolenumCfg)
    linoleumSink(evaluatedSpans)

    env
  }

  /** Auxiliary private method to handle Maude term logging
    */
  private def logMaudeTerms(spanInfos: source.SpanInfoStream): Unit = {
    // Add current working directory to target path
    val targetPath =
      new Path(System.getProperty("user.dir"), SinkConfig.MaudeTermLogPath)
    log.info("Logging traces as Maude terms to {}", targetPath)

    val fileSink = StreamingFileSink
      .forRowFormat(targetPath, new SimpleStringEncoder[String]("UTF-8"))
      .withRollingPolicy(
        DefaultRollingPolicy
          .builder()
          .withRolloverInterval(60 * 1000) // 1 minute
          .withInactivityInterval(60 * 1000) // 1 minute
          .withMaxPartSize(1024 * 1024) // 1 MB
          .build()
      )
      .build()

    spanInfos.map(_.toMaude).addSink(fileSink)
  }
}

package object utils {
  // From https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
  def byteString2HexString(byteString: ByteString): String =
    byteString.toByteArray.map("%02X" format _).mkString.toLowerCase
}

package object maude {
  import utils.byteString2HexString

  object MaudeMonitor {
    case class EvaluationConfig(
        messageRewriteBound: Int = 100
    )
  }

  /** @param name
    *   human-readable description for this property.
    * @param program
    *   Resources path (e.g. "maude/lotrbot_imagegen_safety.maude") for the
    *   Maude program that defines the monitor
    * @param module
    *   Maude module that contains the monitor Maude class, the maude Prop, and
    *   the `_|=_` rules to evaluate Maude objects configurations for that
    *   property
    * @param monitorOid
    *   Maude term for the Oid of the monitor object
    * @param initialSoup
    *   Maude term for the initial soup, that typically includes the initial
    *   monitor object
    * @param property
    *   Maude term for the property for which object configuration will be
    *   evaluated using the `_|=_` rules
    * @param dependencyPrograms
    *   List of resources paths for other dependent programs. Note the trace
    *   types program ("maude/linoleum/trace.maude") defined here is always
    *   loaded additionally
    * @param dependencyStdlibPrograms
    *   List of Maude standard library programs (e.g. "model-checker.maude")
    *   that module depends on. Note "model-checker.maude" is always loaded
    *   additionally
    */
  case class MaudeMonitor(
      name: String,
      program: String,
      module: String,
      monitorOid: String,
      initialSoup: String,
      property: String,
      dependencyPrograms: List[String] = List.empty,
      dependencyStdlibPrograms: List[String] = List.empty,
      config: MaudeMonitor.EvaluationConfig = MaudeMonitor.EvaluationConfig()
  )

  object MaudeModules {
    private lazy val maudeRuntime: MaudeRuntime = {
      // Assuming getInstance returns an already initialized runtime
      val runtime = MaudeRuntime.getInstance()
      checkNotNull(runtime)
      runtime
    }

    def loadModule(
        maudeProgramResourcePath: String,
        moduleName: String
    ): MaudeModule = {
      loadProgram(maudeProgramResourcePath)
      jMaude.getModule(moduleName)
    }

    def loadProgram(
        maudeProgramResourcePath: String
    ): Unit = {
      maudeRuntime.loadFromResources(maudeProgramResourcePath)
    }

    def loadStdLibModule(
        maudeProgramFileName: String,
        moduleName: String
    ): MaudeModule = {
      loadStdLibProgram(maudeProgramFileName)
      jMaude.getModule(moduleName)
    }

    def loadStdLibProgram(
        maudeProgramFileName: String
    ): Unit = {
      maudeRuntime.loadStdlibFileFromResources(maudeProgramFileName)
    }

    lazy val traceTypesModule: MaudeModule =
      loadModule("maude/linoleum/trace.maude", "TRACE-CLASS-OBJECTS")
  }

  /** Return a string representation of a Maude SpanObject
    * (https://github.com/demiourgoi/Linoleum/blob/main/maude/linoleum/trace.maude)
    * corresponding to this object, in a format that can be parsed by
    * `parseTerm` using `es.ucm.maude.bindings` for that module.
    *
    * For Oids we use:
    *   - span: s"$traceId/$spanId", converting the ids to hex strings
    *   - even: s"$traceId/$spanId/$index" where index is the position of the
    *     even in the list of events for the span
    */
  def spanToMaude(span: Span): String = {
    val spanId = byteString2HexString(span.getSpanId())
    val traceId = byteString2HexString(span.getTraceId())
    val parentSpanId = byteString2HexString(span.getParentSpanId())
    val spanOid = s"$traceId/$spanId"

    val events =
      if (span.getEventsList().isEmpty()) "nil"
      else
        span
          .getEventsList()
          .asScala
          .zipWithIndex
          .map { case (event, index) =>
            val eventId = s"$spanOid/$index"
            s"""| < event("$eventId") : Event | 
              | timeUnixNano : ${event.getTimeUnixNano()},
              | name : "${event.getName()}",
              | attributes : ${spanAttributesToMaude(event.getAttributesList())}
            | > """.stripMargin.replaceAll("[\r\n]", "")
          }
          .mkString(" ")

    s"""| < span("$spanOid") : Span |
              | traceId : "$traceId", 
              | spanId : "$spanId",
              | parentSpanId : "$parentSpanId", 
              | name : "${span.getName()}", 
              | startTimeUnixNano : ${span.getStartTimeUnixNano()},
              | endTimeUnixNano : ${span.getEndTimeUnixNano()},
              | attributes : ${spanAttributesToMaude(
         span.getAttributesList()
       )}, 
              | events : $events
          | > """.stripMargin.replaceAll("[\r\n]", "")
  }

  private def spanAttributesToMaude(attributes: jutil.List[KeyValue]): String =
    if (attributes.isEmpty()) "nil"
    else
      attributes.asScala
        .map { keyValueToMaude(_) }
        .mkString(" ")

  private def keyValueToMaude(kv: KeyValue): String =
    s"""["${kv.getKey()}", "${anyValueToMaude(kv.getValue())}"]"""

  private def anyValueToMaude(anyValue: AnyValue): String =
    // Note in ../maude/linoleum/trace.maude we have `op [_,_] : String String -> KeyEvent [ctor] .`
    // so this should always return a string representation of a Maude term of `String` sort.
    anyValue match {
      case av if av.hasStringValue() => stringValueToMaude(av.getStringValue())
      case av if av.hasBoolValue()   => s"${av.getBoolValue()}"
      case av if av.hasIntValue()    => s"${av.getIntValue()}"
      case av if av.hasDoubleValue() => s"${av.getDoubleValue()}"
      case av if av.hasArrayValue()  => "unsupportedArrayValue"
      case av if av.hasKvlistValue() => "unsupportedKvlistValue"
      /*
      This leads to terms like the following that currently are not parseables by Maude

      ["OoDcKjz", "["OK", "["6G", "["8", "34"] ["V", "1.0133370662579862E308"]"] ["e", "true"]"] ["Si", "79"]"],

      Warning: <standard input>, line 0: bad token "["OK", ".
       */
      // case av if av.hasArrayValue() =>
      //   av.getArrayValue()
      //     .getValuesList()
      //     .asScala
      //     .map { anyValueToMaude(_) }
      //     .mkString(" ")
      // case av if av.hasKvlistValue() =>
      //   av.getKvlistValue()
      //     .getValuesList()
      //     .asScala
      //     .map { keyValueToMaude(_) }
      //     .mkString(" ")
      case av if av.hasBytesValue() =>
        s"${byteString2HexString(av.getBytesValue())}"
      case _ => ""
    }

  /** We found some issues handling quoted nested strings with Maude, see
    * https://github.com/demiourgoi/Linoleum/issues/15
    *
    * This implementation replaced nested double quotes with single quotes
    * escaped as "\\'", which transforms nested JSON strings into invalid JSON
    * strings, as JSON requires double quotes enclosing strings
    */
  private def stringValueToMaude(stringValue: String): String =
    // stringValue.replace("\"", "'") // Fake JSON with single quotes
    // StringEscapeUtils.escapeJson(av.getStringValue())
    // stringValue.replace("\"", "\\'") // Fake JSON with escaped single quotes
    stringValue.replace(
      "\"",
      "%22"
    ) // use perc scape for single quotes as in json/json.maude
}
package object formulas {
  import messages._

  /** Formulas use a list of LinoleumEvent ordered by linoleumEventOrdering */
  type Letter = List[LinoleumEvent]

  implicit class LetterOps(self: Letter) {
    def findMatchingSpan(
        matching: PartialFunction[LinoleumEvent, SpanInfo]
    ): Option[SpanInfo] =
      self.collectFirst(matching)
  }

  type TimedLetter = (SscheckTime, Letter)

  type SscheckFormula = Formula[Letter]

  type SscheckFormulaSupplier = () => SscheckFormula

  /** Example
    *
    * ```scala
    * val formulaSupplier = linoleumFormula {
    *   always { x: Letter => x.length > 0 } during 2
    * }
    * ```
    *
    * or to directly create a LinoleumFormula:
    *
    * ```scala
    * val formula = LinoleumFormula(
    *   "test",
    *   linoleumFormula {
    *     always { x: Letter => x.length > 0 } during 2
    *   }
    * )
    * ```
    */
  def linoleumFormula(formula: SscheckFormula): SscheckFormulaSupplier =
    new SscheckFormulaSupplier {
      def apply(): SscheckFormula = formula
    }

  object LinoleumFormula {

    /** Nice way of defining a LinoleumFormula without having to create an
      * intermediate class, e.g. as:
      *
      * \``` val formula = LinoleumFormula("test"){ always { x : Letter =>
      * x.length > 0 } during 2 }```
      *
      * However, in practice that can only we used in unit tests, because Flink
      * tends to throw org.apache.flink.api.common.InvalidProgramException while
      * calling org.apache.flink.api.java.ClosureCleaner.clean at runtime, when
      * usign this method to define a formula inline. A simple fix is just
      * defining the formula in a separate class that extends
      * SscheckFormulaSupplier, and also explicitly Serializable
      */
    def apply(name: String, config: EvaluationConfig)(
        formula: SscheckFormula
    ): LinoleumFormula =
      LinoleumFormula(name, config, linoleumFormula(formula))

    case class EvaluationConfig(
        tickPeriod: Duration,
        sessionGap: Duration,
        allowedLateness: Duration = Duration.ofMillis(0)
    )
  }

  // Note: trying to avoid serializing sscheck formulas by requiring a formula supplier
  // that should be a stateless class that is trivial to serialize
  /** @param name
    *   human-readable description for the formula
    * @param config
    *   formula evaluation configuration
    * @param formula
    *   supplier for the sscheck formula to evaluate. The formula must not
    *   perform any side effect when evaluated.
    */
  case class LinoleumFormula(
      name: String,
      config: LinoleumFormula.EvaluationConfig,
      formula: SscheckFormulaSupplier
  )
}

// https://alvinalexander.com/scala/fp-book/type-classes-101-introduction/
// https://www.baeldung.com/scala/type-classes
trait Property[P] {
  def propertyName(property: P): String

  def streamEvaluatorParams(property: P): SpanStreamEvaluatorParams[P]

  /** Assumes events are ordered and events before the root span (as defined by
    * evaluator.SpanStreamEvaluator.rootSpanFor) are discarded )
    */
  def evaluate(property: P)(orderedEvents: List[LinoleumEvent]): TruthValue
}

object PropertySyntax {
  implicit class PropertyOps[P](value: P) {
    def propertyName(implicit
        propertyInstance: Property[P]
    ): String = {
      propertyInstance.propertyName(value)
    }

    def evaluate(orderedEvents: List[LinoleumEvent])(implicit
        propertyInstance: Property[P]
    ): TruthValue = propertyInstance.evaluate(value)(orderedEvents)

    def streamEvaluatorParams(implicit
        propertyInstance: Property[P]
    ): SpanStreamEvaluatorParams[P] =
      propertyInstance.streamEvaluatorParams(value)
  }
}

@SerialVersionUID(1L)
object PropertyInstances extends Serializable {
  import formulas._
  import evaluator.SpanStreamEvaluator.{rootSpanFor, traceIdFor}

  @SerialVersionUID(1L)
  object FormulaProperty extends Serializable {
    import System.lineSeparator
    import io.github.demiourgoi.sscheck.prop.tl.Formula.defaultFormulaParallelism
    import messages._
    import TimeUtils._

    val log = LoggerFactory.getLogger(FormulaProperty.getClass.getName)

    /** Organizes the events in the trace as a set of discrete letters, that
      * split the sequence of events in tumbling windows of tickPeriod duration.
      * The first letter always starts with `SpanStart(rootSpan)` and events
      * before that are discarded as errors.
      *
      * Precondition: events for rootSpan are NOT added to events
      */
    def buildLetters(formula: LinoleumFormula)(
        orderedEvents: List[LinoleumEvent]
    ): Iterator[TimedLetter] = {
      val startEvent = orderedEvents.head
      val rootSpan = rootSpanFor(orderedEvents)
      log.debug(
        "Building letters for trace {} with rootSpan {} and abridged orderedEvents {}",
        rootSpan.hexTraceId,
        rootSpan.hexSpanId,
        orderedEvents.map { _.shortToString }.mkString(lineSeparator)
      )
      log.debug(
        "Building letters for trace {} with rootSpan {} and orderedEvents {}",
        rootSpan.hexTraceId,
        rootSpan.hexSpanId,
        orderedEvents.map { _.toString() }.mkString(lineSeparator)
      )

      val startTimestampNanos = startEvent.epochUnixNano
      val endTimestampNanos = orderedEvents.last.epochUnixNano
      val tickPeriod = formula.config.tickPeriod
      def tumblingWindowIndex(timestampNanos: Long): Int = {
        val timeOffset = timestampNanos - startTimestampNanos
        (timeOffset / tickPeriod.toNanos()).toInt
      }
      val endingWindowIndex = tumblingWindowIndex(endTimestampNanos)
      Iterator.range(0, endingWindowIndex + 1).map { windowIndex =>
        val windowEvents = orderedEvents.filter { event =>
          tumblingWindowIndex(event.epochUnixNano) == windowIndex
        }
        val letterTime = SscheckTime(
          nanosToMs(startTimestampNanos + windowIndex * tickPeriod.toNanos())
        )
        (letterTime, windowEvents)
      }
    }

    def evaluateLetters(formula: LinoleumFormula)(
        traceId: String,
        letters: Iterator[TimedLetter]
    ): TruthValue = {
      val initialFormula = formula.formula().nextFormula
      val finalFormula = initialFormula.evaluate(
        letters,
        (timedLetter: TimedLetter, currentFormula: NextFormula[Letter]) => {
          val (letterTime, letter) = timedLetter
          log.debug(
            "Current formula for trace id {} at time {} is {}",
            traceId,
            letterTime,
            currentFormula
          )
        }
      )
      formulaToTruthValue(finalFormula)
    }

    /** Builds a TruthValue for the current evaluation state of Formula */
    def formulaToTruthValue[T](formula: NextFormula[T]): TruthValue = {
      import org.scalacheck.Prop
      formula.result match {
        case Some(Prop.True)  => True
        case Some(Prop.False) => False
        case _                => Undecided
      }
    }
  }

  implicit val formulaProperty: Property[LinoleumFormula] =
    new Property[LinoleumFormula] with Serializable {
      import FormulaProperty._

      override def propertyName(formula: LinoleumFormula): String = formula.name

      override def streamEvaluatorParams(
          formula: LinoleumFormula
      ): SpanStreamEvaluatorParams[LinoleumFormula] =
        SpanStreamEvaluatorParams[LinoleumFormula](
          property = formula,
          tickPeriod = formula.config.tickPeriod,
          sessionGap = formula.config.sessionGap,
          allowedLateness = formula.config.allowedLateness
        )

      override def evaluate(formula: LinoleumFormula)(
          orderedEvents: List[LinoleumEvent]
      ): TruthValue = {
        val letters = buildLetters(formula)(orderedEvents)
        val rootSpan = rootSpanFor(orderedEvents)
        val truthValue =
          evaluateLetters(formula)(rootSpan.hexTraceId, letters.iterator)
        truthValue
      }
    }

  @SerialVersionUID(1L)
  object MaudeMonitorProperty extends Serializable {
    import maude._
    import es.ucm.maude.bindings._

    val log = LoggerFactory.getLogger(MaudeMonitorProperty.getClass.getName)

    def soupToTruthValue(propertyModule: Module, maudeProperty: String)(
        soup: Term
    ): TruthValue = {
      val truthTerm =
        propertyModule.parseTerm(s"""${soup.toString} |= $maudeProperty""")
      checkNotNull(truthTerm)
      truthTerm.reduce()
      truthTerm.toString match {
        case "true"  => True
        case "false" => False
        case _       => Undecided
      }
    }

    /** Loads the trace types module, dependency programs, and finally loads and
      * returns the module for the Maude monitor program.
      *
      * This always loads "model-checker.maude" as an additional dependency,
      * because the SATISFACTION module is required to evalute MaudeMonitors to
      * a truth value
      *
      * This checks that all loaded Maude modules are not null
      */
    def ensureModulesLoaded(mon: MaudeMonitor): Module = {
      checkNotNull(MaudeModules.traceTypesModule)
      mon.dependencyPrograms.foreach(MaudeModules.loadProgram)
      (mon.dependencyStdlibPrograms.toSet + "model-checker.maude")
        .foreach(MaudeModules.loadStdLibProgram)
      val monitorModule = MaudeModules.loadModule(mon.program, mon.module)
      checkNotNull(monitorModule)
      monitorModule
    }

    /** Loads the Maude monitor modules and initializes the soup as specified in
      * mon. Then this traverses the events, sending each event to
      * mon.monitorOid and rewriting the soup; finally it returns the truth
      * value of the soup according to the Maude property specific in mon
      *
      * Optionally, a callback can be specified to inspect how the monitor
      * changes with every event
      */
    def evaluateWithCallback(mon: MaudeMonitor)(
        orderedEvents: List[LinoleumEvent],
        onEvaluationStep: Option[(LinoleumEvent, Term, TruthValue) => Unit] =
          None
    ): TruthValue = {
      val traceId = traceIdFor(orderedEvents)
      val monitorModule = ensureModulesLoaded(mon)
      var soup = monitorModule.parseTerm(s"""${mon.initialSoup}"""")
      checkNotNull(soup)
      log.debug(
        "Evaluating monitor {} for traceId {}: initial soup [{}]",
        mon,
        traceId,
        soup
      )

      val getTruthValue = soupToTruthValue(monitorModule, mon.property)(_)

      orderedEvents.foreach { event =>
        soup = monitorModule.parseTerm(
          s"""${event.toMaude(mon.monitorOid)} $soup""""
        )
        checkNotNull(soup)
        soup.rewrite(mon.config.messageRewriteBound)
        log.debug(
          "Evaluating monitor {} for traceId {}: after event {}, current soup [{}]",
          mon.name,
          traceId,
          event,
          soup
        )
        onEvaluationStep.foreach { cb =>
          cb(event, soup, getTruthValue(soup))
        }
      }

      getTruthValue(soup)
    }

    /** Uses evaluateWithCallback returning on the second element of the tuple
      * the Maude term for the soup and its truth value after each event is
      * processed
      */
    def evaluateWithSteps(mon: MaudeMonitor)(
        orderedEvents: List[LinoleumEvent]
    ): (TruthValue, List[(String, TruthValue)]) = {
      import scala.collection.mutable.ListBuffer

      val soups = new ListBuffer[(String, TruthValue)]
      val truthValue = evaluateWithCallback(mon)(
        orderedEvents,
        Some((event, soup, truthVal) => {
          soups.addOne((soup.toString, truthVal))
        })
      )

      (truthValue, soups.toList)
    }

    def evaluate(
        mon: MaudeMonitor
    )(orderedEvents: List[LinoleumEvent]): TruthValue = {
      evaluateWithCallback(mon)(orderedEvents)
    }
  }

}

sealed trait TruthValue

@SerialVersionUID(1L)
case object True extends TruthValue with Serializable

@SerialVersionUID(1L)
case object False extends TruthValue with Serializable

@SerialVersionUID(1L)
case object Undecided extends TruthValue with Serializable

object TimeUtils {
  val million = pow(10, 6)

  def nanosToMs(nanos: Long): Long = (nanos / million).longValue

  def msToNanos(ms: Long): Long = ms * million.longValue

  def instantToTimestamp(instant: Instant): Timestamp =
    Timestamp
      .newBuilder()
      .setSeconds(instant.getEpochSecond)
      .setNanos(instant.getNano)
      .build()

  def timestampToInstant(timestamp: Timestamp): Instant =
    Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos)
}

/** The result of evaluating a trace according to a property
  *
  * @param propertyName
  *   Name of the property that was evaluated on the trace.
  * @param rootTraceHexId
  *   Trace id in hex format for the root spand of the evaluated trace.
  * @param traceStartTimeUnixNano
  *   Start time of the evaluated trace. Note this is stable even when late
  *   spans arrive, that's why we use the start and not the end time.
  * @param truthValue
  *   Value to which the property is evaluated to for this trace.
  */
@SerialVersionUID(1L)
case class EvaluatedTrace(
    rootTraceHexId: String,
    traceStartTimeUnixNano: Long,
    propertyName: String,
    truthValue: TruthValue
) {
  import org.bson._
  import TimeUtils.nanosToMs

  /** @return
    *   a BSON document for this evaluated trace with fields:
    *
    *   - propertyName: this.propertyName, expected to be used as metaField
    *     identifying the time series to MongoDB
    *   - traceStartDate: this.traceStartTimeUnixNano, expected to be used as
    *     timeField for
    * the MongoDB time series
    *   - traceId: this.rootTraceHexId
    *   - evaluationDate: mongo date for the time this method is called.
    *   - truthValue: this.truthValue
    *
    * The _id of the document is autogenerated instead of being defined in terms
    * of (trace id, date, propertyName), which is not possible due to
    * limitations in MongoDB supported data types. This implies that if the same
    * trace is reevaluated then it would have more than 1 document in the target
    * database and collection. This field evaluationDate can be used to
    * distinguish evaluations, and do things like only querying the latest
    * evaluations.
    */
  def toBsonDocument: BsonDocument = {
    /*
    https://www.mongodb.com/docs/drivers/java/sync/v4.5/fundamentals/data-formats/documents/#bsondocument
    shows BsonDateTime takes epoch millis
    https://javadoc.io/static/org.mongodb/bson/3.12.10/org/bson/types/ObjectId.html#%3Cinit%3E(java.lang.String)
    shows BSON expects 24-byte hexadecimal string representation, while https://opentelemetry.io/docs/specs/otel/trace/api/#retrieving-the-traceid-and-spanid
    shows OTEL produces 32-hex characters for trace ids. The constructor of `ObjectId` fails when provided
    a trace id with too many bytes.
     */
    val now = Instant.now()
    new BsonDocument()
      .append("traceId", new BsonString(rootTraceHexId))
      .append(
        "traceStartDate",
        new BsonDateTime(nanosToMs(traceStartTimeUnixNano))
      )
      .append("evaluationDate", new BsonDateTime(now.toEpochMilli()))
      .append("propertyName", new BsonString(propertyName))
      .append("truthValue", new BsonString(truthValue.toString()))
  }
}

package object evaluator {
  type VerifiedTraceStream = DataStream[EvaluatedTrace]
}
package evaluator {
  import io.github.demiourgoi.sscheck.prop.tl.Formula.defaultFormulaParallelism
  import org.apache.flink.streaming.api.windowing.windows.TimeWindow
  import org.apache.flink.util.Collector

  import java.util
  import scala.collection.mutable.ListBuffer
  import scala.util.Failure

  /** @param tickPeriod
    *   \- The size of the tumbling windows we use to split the sequence of span
    *   events.
    * @param sessionGap
    *   \- When the formula doesn't have a defined safe world length, this is
    *   how much we wait for a new span event to arrive before we close the set
    *   of spans for a trace.
    * @param allowedLateness
    *   \- How much we wait for late spans to appear before we close the set of
    *   spans. If greater than 0 then we might evaluate a formula on a trace
    *   more than once: on session gap completion (watermark), and also on late
    *   elements arriving. Those additional evaluations could change the
    *   evaluation of a trace. See https://github.com/juanrh/Linoleum/issues/3
    *   for more details.
    */
  case class SpanStreamEvaluatorParams[P](
      property: P,
      tickPeriod: Duration,
      sessionGap: Duration,
      allowedLateness: Duration
  )(implicit propInstance: Property[P])

  /** For each span in a trace we emit a SpanStart and SpanEnd event, and order
    * all the events using the span start time for SpanStart events and the end
    * time for SpanEnd events. We then discretize the ordered sequence using
    * tumbling windows of `tickPeriod` duration. The first letter always starts
    * with `SpanStart(rootSpan)` and events before that are discarded as errors.
    *
    * Note a limitation is that if the clocks in the different hosts that emit
    * the spans are too skewed then we could lose the happens-before relation
    * between parent and child spans when we order by event timestamp, that is
    * inherited from the span timestamps. This is a known limitation to be
    * tackled on https://github.com/juanrh/Linoleum/issues/4
    *
    * If a span gets into the event more than once (two SpanInfo have the same
    * spanId) then we only process one of them. One of the replicas is chosen
    * arbitrarily, so this assumes the trace instrumentation libraries only use
    * the same span id for identical spans.
    */

  @SerialVersionUID(1L)
  class SpanStreamEvaluator[P](
      @transient private val params: SpanStreamEvaluatorParams[P]
  )(implicit propInstance: Property[P])
      extends Function[SpanInfoStream, VerifiedTraceStream]
      with Serializable {

    import SpanStreamEvaluator._

    override def apply(spanStream: SpanInfoStream): VerifiedTraceStream = {
      val traceStream = spanStream.keyBy { span: SpanInfo =>
        span.getSpan.getTraceId
      }

      traceStream
        .window(EventTimeSessionWindows.withGap(params.sessionGap))
        .allowedLateness(params.allowedLateness)
        .process(processWindow())
    }
    private[evaluator] def processWindow() =
      new ProcessWindow[TimeWindow, P](params)
  }

  object SpanStreamEvaluator {
    private val log =
      LoggerFactory.getLogger(SpanStreamEvaluator.getClass.getName)

    /** Gets the root span for orderedEvents, assuming that list is not empty
      * and the first event is a start event for the root span
      */
    def rootSpanFor(orderedEvents: List[LinoleumEvent]): SpanInfo =
      orderedEvents.head.span

    def traceIdFor(orderedEvents: List[LinoleumEvent]): String =
      rootSpanFor(orderedEvents).hexTraceId

    object EventCollectionMultipleRootSpansError {
      import io.github.demiourgoi.linoleum.messages._

      def message(rootSpanInfo: SpanInfo, spanInfo: SpanInfo) =
        s"Multiple root spans found for trace with id ${rootSpanInfo.hexTraceId}: $rootSpanInfo, $spanInfo"
    }
    class EventCollectionMultipleRootSpansError(
        rootSpanInfo: SpanInfo,
        spanInfo: SpanInfo
    ) extends EventCollectionError(
          EventCollectionMultipleRootSpansError.message(rootSpanInfo, spanInfo)
        )

    class EventCollectionError(message: String) extends Exception(message)

    @SerialVersionUID(1L)
    private[evaluator] class ProcessWindow[W <: TimeWindow, P](
        private val params: SpanStreamEvaluatorParams[P]
    )(implicit propInstance: Property[P])
        extends ProcessWindowFunction[SpanInfo, EvaluatedTrace, ByteString, W] {
      import messages._

      override def process(
          key: ByteString,
          context: ProcessWindowFunction[
            SpanInfo,
            EvaluatedTrace,
            ByteString,
            W
          ]#Context,
          spanInfos: jlang.Iterable[SpanInfo],
          out: Collector[EvaluatedTrace]
      ): Unit = {
        import PropertySyntax.PropertyOps
        import PropertyInstances.formulaProperty

        // Build letters starting from the root span
        val (rootSpanOpt, events, failures) = collectLinoleumEvents(spanInfos)
        // TODO use side output to handle errors with simple error event with level, message and span, defined in proto
        failures.foreach { t => log.error(t.exception.getMessage()) }

        rootSpanOpt.fold({
          // If the root span is missing then this is a window for a late span not added to the first session,
          // so we just discard this window
          // Per https://opentelemetry.io/docs/concepts/signals/traces/ there is always a single root span on every trace
          val someEvents = events.take(5)
          if (someEvents.nonEmpty) {
            log.warn(
              "Found late window for trace with id {}, skipping events {}, ... ",
              someEvents.head.span.hexTraceId,
              someEvents.mkString(", ")
            )
          }
        }) { rootSpan =>
          log.info("Evaluating trace with id {}", rootSpan.hexTraceId)
          val property = params.property
          val orderedEvents = orderEvents(rootSpan, events)
          val truthValue = property.evaluate(orderedEvents)
          val evaluatedTrace = EvaluatedTrace(
            rootSpan.hexTraceId,
            rootSpan.getSpan.getStartTimeUnixNano,
            property.propertyName,
            truthValue
          )
          log.info(
            s"Evaluated trace with id {} to {}",
            rootSpan.hexTraceId,
            evaluatedTrace
          )
          out.collect(evaluatedTrace)
        }
      }

      private[evaluator] def orderEvents(
          rootSpan: SpanInfo,
          events: ListBuffer[LinoleumEvent]
      ): List[LinoleumEvent] = {
        events.addOne(SpanEnd(rootSpan))
        val startEvent = SpanStart(rootSpan)
        val eventsOnTime = events.flatMap { event =>
          if (event.epochUnixNano < startEvent.epochUnixNano) {
            // TODO side output for warning and recoverable errors; stream main output for formula evaluation errors
            log.error(
              "Dropping event {} happening before root letter start {}",
              event,
              startEvent.epochUnixNano
            )
            List.empty
          } else List(event)
        }

        val orderedEvents =
          startEvent :: eventsOnTime.sorted(linoleumEventOrdering).toList
        orderedEvents
      }

      private[evaluator] def collectLinoleumEvents(
          spanInfos: jlang.Iterable[SpanInfo]
      ): (
          Option[SpanInfo],
          ListBuffer[LinoleumEvent],
          List[Failure[LinoleumEvent]]
      ) = {
        val failures = new ListBuffer[Failure[LinoleumEvent]]
        var rootSpanOpt: Option[SpanInfo] = None
        val events = new ListBuffer[LinoleumEvent]
        val seenSpans = new util.HashSet[ByteString]()
        val eventFactories = List(SpanStart, SpanEnd)
        spanInfos.forEach { spanInfo =>
          val spanId = spanInfo.getSpan.getSpanId
          if (seenSpans.contains(spanId)) {
            log.warn("Skipping duplicate occurrence of span {}", spanInfo)
          } else {
            seenSpans.add(spanId)
            (spanInfo.isRoot, rootSpanOpt) match {
              case (true, None) =>
                log.info(
                  "Found root span with span id {}, for trace with id {}",
                  spanInfo.hexSpanId,
                  spanInfo.hexTraceId
                )
                rootSpanOpt = Some(spanInfo)

              case (true, Some(rootSpanInfo)) =>
                failures.addOne(
                  Failure(
                    new EventCollectionMultipleRootSpansError(
                      rootSpanInfo,
                      spanInfo
                    )
                  )
                )

              case _ =>
                events.addAll(eventFactories.map { _.apply(spanInfo) })
            }
          }
        }

        (rootSpanOpt, events, failures.toList)
      }
    }
  }
}
