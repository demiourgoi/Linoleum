package io.github.demiourgoi.linoleum.examples

import java.time.Duration
import org.slf4j.LoggerFactory

import io.github.demiourgoi.linoleum.ltlss.{LinoleumLtlss,SpanInfo}
import io.github.demiourgoi.linoleum.ltlss.config._
import io.github.demiourgoi.linoleum.ltlss.formulas._
import io.github.demiourgoi.linoleum.ltlss.messages._

object Main {
    private val log = LoggerFactory.getLogger(Main.getClass.getName)

    /**
     * Sscheck version of Maude's
     * 
     * red modelCheck(init, [] (clientHasTask(task(1)) -> <> dbHasResult(task(1)) )) .
     * 
    */
    @SerialVersionUID(1L)
    private class HelloFormula extends SscheckFormulaSupplier with Serializable {
        import io.github.demiourgoi.sscheck.prop.tl.Formula._
        import org.specs2.matcher.MustMatchers._

        def apply() = {
            val clientHasTaskSpanName = "client-taskId-assigned"
            val workDoneInDBSpanName = "work-done-db"

            always {
                ifMatches[Letter, SpanInfo]{ _.findMatchingSpan{
                    case SpanStart(span) if span.isNamed(clientHasTaskSpanName) => {
                        log.info("Found span for assigned task with trace id {} and span id {}", span.hexTraceId, span.hexSpanId)
                        span
                    }
                  }
                } ==> { taskAssignedSpan =>
                    later { events: Letter =>
                        events.findMatchingSpan{case SpanEnd(span) if span.isNamed(workDoneInDBSpanName) => span} must beSome
                    } on 10
                }
            } during 5
        }
    }

    def main(args: Array[String]): Unit = {
        val formula = LinoleumFormula("Luego basic liveness", new HelloFormula())
        val cfg = LinoleumConfig(
            jobName = "hello spans", localFlinkEnv = true,
            evaluation = EvaluationConfig(
                tickPeriod=Duration.ofMillis(100), sessionGap=Duration.ofSeconds(1)
            )    
        )

        log.warn("Evaluating traces for formula {}", formula)

        LinoleumLtlss.execute(cfg)(formula)

        log.warn("Ending program")
    }
}
