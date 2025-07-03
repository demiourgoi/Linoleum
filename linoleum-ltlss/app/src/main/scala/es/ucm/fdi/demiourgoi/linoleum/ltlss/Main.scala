package es.ucm.fdi.demiourgoi.linoleum.ltlss

import org.slf4j.LoggerFactory
import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
// FIXME make specs2 matchers automatically imported in the scope
// of LinoleumFormula
import org.specs2.matcher.MustMatchers._
import java.time.Duration

import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.mongodb.sink.MongoSink
import com.mongodb.client.model.InsertOneModel
import es.ucm.fdi.demiourgoi.linoleum.ltlss.messages.LinoleumSpanInfo

object Main {
    import source._
    import config._
    import evaluator._
    import formulas._

    private val log = LoggerFactory.getLogger(Main.getClass.getName)

    /**
     * Sscheck version of Maude's
     * 
     * red modelCheck(init, [] (clientHasTask(task(1)) -> <> dbHasResult(task(1)) )) .
     * 
    */
    @SerialVersionUID(1L)
    private class HelloFormula extends SscheckFormulaSupplier { 
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
        log.warn("Starting program for formula {}", formula)
        val linolenumCfg = LinoleumConfig(localFlinkEnv = true)
        val env = LinoleumSrc.flinkEnv(linolenumCfg)
        val linoleumSrc = new LinoleumSrc(linolenumCfg)
        val spanInfos = linoleumSrc(env)

        val spamEvaluator = new SpanStreamEvaluator(SpanStreamEvaluatorParams(
            formula=formula,
            // FIXME take from LinoleumConfig, as a method of LinoleumConfig
            tickPeriod=Duration.ofMillis(100),
            sessionGap=Duration.ofSeconds(1)
        ))
        val evaluatedSpans = spamEvaluator(spanInfos)

        evaluatedSpans.print()

        import sink.LinoleumSink
        val linoleumSink = new LinoleumSink(linolenumCfg)
        linoleumSink(evaluatedSpans)

        env.execute("hello spans")

        log.warn("Ending program")
        println("bye")
    }
}
