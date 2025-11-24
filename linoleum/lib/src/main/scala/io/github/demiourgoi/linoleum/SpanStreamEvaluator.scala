package io.github.demiourgoi.linoleum

import scala.jdk.CollectionConverters._

import org.scalacheck.Prop
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.core.fs.Path
import org.apache.flink.api.common.serialization.SimpleStringEncoder
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy

import org.apache.commons.text.StringEscapeUtils

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

import messages.SpanInfo

object Linoleum {
  import config._
  import source._
  import sink._
  import evaluator._
  import formulas._
  import maude._

  private val log = LoggerFactory.getLogger(Linoleum.getClass.getName)

  def execute(cfg: LinoleumConfig, maudeJob: MaudeJob): Unit = {
    // FIXME this should be done in all relevant JVMs
    // MaudeRuntime.init()
    // TODO: this is a poor abstraction (stdlib loading not considered for example). But make this
    // easy so the user focuses on the Maude code: e.g. require an entry point module
    // maudeJob.programPaths.foreach { MaudeRuntime.loadFromResources(_) }
    // val maudeModules = maudeJob.maudeModules.map { jMaude.getModule(_) }
  }

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

    val spamEvaluator = new SpanStreamEvaluator(
      SpanStreamEvaluatorParams(linolenumCfg, formula = formula)
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

  /** @param name
    *   human-readable description for this job.
    * @param programPaths
    *   resources paths in the jar for Maude source file for this job.
    * @param maudeModules
    *   Maude modules to load for this job.
    */
  case class MaudeJob(
      name: String,
      programPaths: List[String],
      maudeModules: List[String]
  )

  object MaudeModules {
    private lazy val maudeRuntime: MaudeRuntime = {
      // Assuming getInstance returns an already initialized runtime
      val runtime = MaudeRuntime.getInstance()
      checkNotNull(runtime)
      runtime
    }

    private def loadModule(
        maudeProgramResourcePath: String,
        moduleName: String
    ): MaudeModule = {
      maudeRuntime.loadFromResources(maudeProgramResourcePath)
      jMaude.getModule(moduleName)
    }

    lazy val traceTypesModule: MaudeModule =
      loadModule("maude/linoleum/trace.maude", "CLASS-OBJECTS")
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
      case av if av.hasStringValue() => av.getStringValue() // StringEscapeUtils.escapeJson(av.getStringValue())
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
}
package object formulas {
  import messages._
  sealed trait LinoleumEvent {

    /** Returns UNIX Epoch time in nanoseconds positioning this event on the
      * window for the spans of a trace
      * https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
      */
    def epochUnixNano: Long

    def span: SpanInfo

    def shortToString: String
  }

  case class SpanStart(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getStartTimeUnixNano

    override def shortToString: String =
      s"SpanStart($epochUnixNano, ${span.shortToString})"
  }

  case class SpanEnd(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getEndTimeUnixNano

    override def shortToString: String =
      s"SpanEnd($epochUnixNano, ${span.shortToString})"
  }

  /** Positions start events based on the span start, and end events based on
    * the span end
    */
  implicit val linoleumEventOrdering: Ordering[LinoleumEvent] =
    Ordering[Long].on(_.epochUnixNano)

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
    def apply(name: String)(formula: SscheckFormula): LinoleumFormula =
      LinoleumFormula(name, linoleumFormula(formula))
  }
  // Note: trying to avoid serializing sscheck formulas by requiring a formula supplier
  // that should be a stateless class that is trivial to serialize
  /** @param name
    *   human-readable description for the formula
    * @param formula
    *   supplier for the sscheck formula to evaluate. The formula must not
    *   perform any side effect when evaluated.
    */
  case class LinoleumFormula(name: String, formula: SscheckFormulaSupplier)
}

object FormulaValue {

  /** Builds a FormulaValue for the current evaluation state of Formula */
  def apply[T](formula: NextFormula[T]): FormulaValue =
    formula.result match {
      case Some(Prop.True)  => True
      case Some(Prop.False) => False
      case _                => Undecided
    }
}

sealed trait FormulaValue

@SerialVersionUID(1L)
case object True extends FormulaValue with Serializable

@SerialVersionUID(1L)
case object False extends FormulaValue with Serializable

@SerialVersionUID(1L)
case object Undecided extends FormulaValue with Serializable

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

/** The result of evaluating a trace
  *
  * @param formulaName
  *   Name of the formula that was evaluated on the evaluated trace.
  * @param rootTraceHexId
  *   Trace id in hex format for the root spand of the evaluated trace.
  * @param traceStartTimeUnixNano
  *   Start time of the evaluated trace. Note this is stable even when late
  *   spans arrive, that's why we use the start and not the end time.
  * @param formulaValue
  *   Value to which the formula is evaluated to for this trace.
  */
@SerialVersionUID(1L)
case class EvaluatedTrace(
    rootTraceHexId: String,
    traceStartTimeUnixNano: Long,
    formulaName: String,
    formulaValue: FormulaValue
) {
  import org.bson._
  import TimeUtils.nanosToMs

  /** @return
    *   a BSON document for this evaluated trace with fields:
    *
    *   - formulaName: this.formulaName, expected to be used as metaField
    *     identifying the time series to MongoDB
    *   - traceStartDate: this.traceStartTimeUnixNano, expected to be used as
    *     timeField for
    * the MongoDB time series
    *   - traceId: this.rootTraceHexId
    *   - evaluationDate: mongo date for the time this method is called.
    *   - formulaValue: this.formulaValue
    *
    * The _id of the document is autogenerated instead of being defined in terms
    * of (trace id, date, formulaName), which is not possible due to limitations
    * in MongoDB supported data types. This implies that if the same trace is
    * reevaluated then it would have more than 1 document in the target database
    * and collection. This field evaluationDate can be used to distinguish
    * evaluations, and do things like only querying the latest evaluations.
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
      .append("formulaName", new BsonString(formulaName))
      .append("formulaValue", new BsonString(formulaValue.toString()))
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

  object SpanStreamEvaluatorParams {
    def apply(
        cfg: config.LinoleumConfig,
        formula: formulas.LinoleumFormula
    ): SpanStreamEvaluatorParams = {
      val evalCfg = cfg.evaluation
      SpanStreamEvaluatorParams(
        formula,
        evalCfg.tickPeriod,
        evalCfg.sessionGap,
        evalCfg.allowedLateness
      )
    }

  }

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
  case class SpanStreamEvaluatorParams(
      formula: formulas.LinoleumFormula,
      tickPeriod: Duration,
      sessionGap: Duration,
      allowedLateness: Duration
  )

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
  class SpanStreamEvaluator(
      @transient private val params: SpanStreamEvaluatorParams
  ) extends Function[SpanInfoStream, VerifiedTraceStream]
      with Serializable {

    import SpanStreamEvaluator._

    override def apply(spanStream: SpanInfoStream): VerifiedTraceStream = {
      val traceStream = spanStream.keyBy { span: SpanInfo =>
        span.getSpan.getTraceId
      }

      traceStream
        .window(EventTimeSessionWindows.withGap(params.sessionGap))
        .allowedLateness(params.allowedLateness)
        .process(processWindow)
    }

    private[evaluator] def processWindow =
      new ProcessWindow[TimeWindow](params.formula, params.tickPeriod)
  }

  object SpanStreamEvaluator {
    private val log =
      LoggerFactory.getLogger(SpanStreamEvaluator.getClass.getName)

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
    private[evaluator] class ProcessWindow[W <: TimeWindow](
        private val formula: formulas.LinoleumFormula,
        private val tickPeriod: Duration
    ) extends ProcessWindowFunction[SpanInfo, EvaluatedTrace, ByteString, W] {

      import formulas._
      import messages._
      import TimeUtils._
      import System.lineSeparator

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
          val formulaValue =
            evaluateFormula(rootSpan.hexTraceId, buildLetters(rootSpan, events))
          val evaluatedTrace = EvaluatedTrace(
            rootSpan.hexTraceId,
            rootSpan.getSpan.getStartTimeUnixNano,
            formula.name,
            formulaValue
          )
          log.info(
            s"Evaluated trace with id {} to {}",
            rootSpan.hexTraceId,
            evaluatedTrace
          )
          out.collect(evaluatedTrace)
        }
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

      /** Organizes the events in the trace as a set of discrete letters, that
        * split the sequence of events in tumbling windows of tickPeriod
        * duration. The first letter always starts with `SpanStart(rootSpan)`
        * and events before that are discarded as errors.
        *
        * Precondition: events for rootSpan are NOT added to events
        */
      private[evaluator] def buildLetters(
          rootSpan: SpanInfo,
          events: ListBuffer[LinoleumEvent]
      ): Iterator[TimedLetter] = {
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

      private[evaluator] def evaluateFormula(
          traceId: String,
          letters: Iterator[TimedLetter]
      ): FormulaValue = {
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
        FormulaValue(finalFormula)
      }
    }
  }
}
