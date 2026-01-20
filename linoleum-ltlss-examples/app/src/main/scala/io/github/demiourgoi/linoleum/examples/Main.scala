package io.github.demiourgoi.linoleum.examples

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

import io.github.demiourgoi.linoleum.Linoleum
import io.github.demiourgoi.linoleum.config._
import io.github.demiourgoi.linoleum.formulas._
import io.github.demiourgoi.linoleum.messages._

package object sscheckBasicLivenessFormula {
  val log = LoggerFactory.getLogger(
    "io.github.demiourgoi.linoleum.examples.sscheckBasicLivenessFormula"
  )

  /** Sscheck version of Maude's
    *
    * red modelCheck(init, [] (clientHasTask(task(1)) -> <> dbHasResult(task(1))
    * )) .
    */
  @SerialVersionUID(1L)
  private class HelloFormula extends SscheckFormulaSupplier with Serializable {
    import io.github.demiourgoi.sscheck.prop.tl.Formula._
    import org.specs2.matcher.MustMatchers._

    def apply() = {
      val clientHasTaskSpanName = "client-taskId-assigned"
      val workDoneInDBSpanName = "work-done-db"

      always {
        ifMatches[Letter, SpanInfo] {
          _.findMatchingSpan {
            case SpanStart(span) if span.isNamed(clientHasTaskSpanName) => {
              log.info(
                "Found span for assigned task with trace id {} and span id {}",
                span.hexTraceId,
                span.hexSpanId
              )
              span
            }
          }
        } ==> { taskAssignedSpan =>
          later { events: Letter =>
            events.findMatchingSpan {
              case SpanEnd(span) if span.isNamed(workDoneInDBSpanName) => span
            } must beSome
          } on 10
        }
      } during 5
    }
  }

  def run(): Unit = {
    import Main.localCfg

    val formula = LinoleumFormula(
      "Luego basic liveness",
      LinoleumFormula.EvaluationConfig(
        tickPeriod = Duration.ofMillis(100),
        sessionGap = Duration.ofSeconds(1)
      ),
      new HelloFormula()
    )

    log.warn("Evaluating traces for formula {}", formula)
    Linoleum.execute(localCfg.copy(jobName = "hello spans"), formula)
    log.warn("Ending program")
  }
}

package object maudeLotrImageGenSafetyMonitor {
  import io.github.demiourgoi.linoleum.maude._
  import Main.localCfg

  val log = LoggerFactory.getLogger(
    "io.github.demiourgoi.linoleum.examples.maudeLotrImageGenSafetyMonitor"
  )

  // Groups together by the value of the attribute key "gen_ai.agent.name" 
  // if it exists, otherwise by trace id
  def keyByAgentName(span: SpanInfo): String = {
    val agentNameOpt = span
      .getSpan()
      .getAttributesList()
      .asScala
      .toList
      .collectFirst {
        case kv
            if (kv.getKey() == "gen_ai.agent.name") && (kv
              .getValue()
              .hasStringValue()) =>
          kv.getValue().getStringValue()
      }

    agentNameOpt.getOrElse(span.hexTraceId)
  }

  def shouldIgnoreWindowOlderThanOneDay(agentName: String, events: List[LinoleumEvent]): Boolean = {
    // Using agent names with format f"lotrbot/{uuid.uuid4()}/{int(time.time())}"
    val epoch = agentName.split("/").last.toLong
    Instant.ofEpochSecond(epoch).isBefore(Instant.now().minus(1, ChronoUnit.DAYS))
  }

  val stateConfig = MaudeMonitor.StateConfig(
    ttl = Duration.ofDays(1),
    // note Linoleum refreshes TTL on state read
    shouldIgnoreWindow=shouldIgnoreWindowOlderThanOneDay
  )

  val monOid = s"""mon("safety")"""
  val monitor =
    MaudeMonitor(
      name = "lotrbot does not spend too much time generating images",
      program = "maude/lotrbot_imagegen_safety.maude",
      module = "IMAGEGEN-SAFETY-PROPS",
      monitorOid = monOid,
      initialSoup = s"""initConfig($monOid)""",
      property = "imageGenUsageWithinLimits",
      keyBy = Some(keyByAgentName),
      config = MaudeMonitor.EvaluationConfig(
        messageRewriteBound = 100,
        sessionGap = Duration.ofSeconds(5)
      )
    )

  /*
  Check with
  
  $ grep  "rewritten to current soup" run.log

  and check first occurrence changing from zero.
  Note we might need two lotrbot runs to push the events
   */
  def run(): Unit = {
    log.warn("Running maudeLotrImageGenSafetyMonitor example")
    Linoleum.execute(
      localCfg.copy(jobName = "maudeLotrImageGenSafetyMonitor"),
      monitor
    )
    log.warn("Ending program")
  }
}

object Main {
  private val log = LoggerFactory.getLogger(Main.getClass.getName)
  val localCfg = LinoleumConfig(
    jobName = "",
    localFlinkEnv = true,
    sink = SinkConfig().copy(logMaudeTerms = true)
  )

  object ExampleId extends Enumeration {
    type ExampleId = Value
    val SscheckBasicLiveness, MaudeLotrImageGenSafety = Value
  }

  /** Parses the ExampleId from command line arguments.
    * @param args
    *   Command line arguments
    * @return
    *   The parsed ExampleId, defaults to HelloSscheckFormula if not specified
    *   or invalid
    */
  private def parseExampleId(args: Array[String]): Option[ExampleId.Value] = {
    if (args.nonEmpty) {
      ExampleId.values
        .find(_.toString == args(0))
    } else {
      None
    }
  }

  def main(args: Array[String]): Unit = {
    val exampleId = parseExampleId(args)
    log.info("Running example program with id '{}'", exampleId)
    exampleId match {
      case None => log.error("Unknown example program name")
      case Some(ExampleId.SscheckBasicLiveness) =>
        sscheckBasicLivenessFormula.run()
      case Some(ExampleId.MaudeLotrImageGenSafety) =>
        maudeLotrImageGenSafetyMonitor.run()
      case _ => throw new NotImplementedError("WIP")
    }
  }
}
