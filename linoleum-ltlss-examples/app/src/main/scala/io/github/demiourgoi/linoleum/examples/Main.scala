package io.github.demiourgoi.linoleum.examples

import java.time.Duration
import org.slf4j.LoggerFactory

import io.github.demiourgoi.linoleum.Linoleum
import io.github.demiourgoi.linoleum.config._
import io.github.demiourgoi.linoleum.formulas._
import io.github.demiourgoi.linoleum.messages._

package object helloSscheckFormula {
  val log = LoggerFactory.getLogger(
    "io.github.demiourgoi.linoleum.examples.helloSscheckFormula"
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
    val formula = LinoleumFormula("Luego basic liveness", new HelloFormula())
    val cfg = LinoleumConfig(
      jobName = "hello spans",
      localFlinkEnv = true,
      sink = SinkConfig().copy(logMaudeTerms = true),
      evaluation = EvaluationConfig(
        tickPeriod = Duration.ofMillis(100),
        sessionGap = Duration.ofSeconds(1)
      )
    )

    log.warn("Evaluating traces for formula {}", formula)

    Linoleum.execute(cfg, formula)

    log.warn("Ending program")
  }
}

object Main {
  private val log = LoggerFactory.getLogger(Main.getClass.getName)

  object ExampleId extends Enumeration {
    type ExampleId = Value
    val HelloSscheckFormula, MaudeLotrImageGenSafety = Value
  }

  /** Parses the ExampleId from command line arguments.
    * @param args
    *   Command line arguments
    * @return
    *   The parsed ExampleId, defaults to HelloSscheckFormula if not specified
    *   or invalid
    */
  private def parseExampleId(args: Array[String]): ExampleId.Value = {
    if (args.nonEmpty) {
      ExampleId.values
        .find(_.toString == args(0))
        .getOrElse(ExampleId.HelloSscheckFormula)
    } else {
      ExampleId.HelloSscheckFormula
    }
  }

  def main(args: Array[String]): Unit = {
    val exampleId = parseExampleId(args)
    log.info("Running example program with id '{}'", exampleId)
    exampleId match {
      case ExampleId.HelloSscheckFormula => helloSscheckFormula.run()
      case _ => throw new NotImplementedError("WIP")
    }
  }
}
