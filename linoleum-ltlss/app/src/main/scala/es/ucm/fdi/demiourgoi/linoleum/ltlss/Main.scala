package es.ucm.fdi.demiourgoi.linoleum.ltlss

import org.slf4j.LoggerFactory
import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
// FIXME make specs2 matchers automatically imported in the scope
// of LinoleumFormula
import org.specs2.matcher.MustMatchers._
import java.time.Duration
import java.util.function.Supplier

object Main {
    import source._
    import evaluator._
    import formulas._

    private val log = LoggerFactory.getLogger(Main.getClass.getName)

    // FIXME new trait with both formula supplier and serializable
    // FIXME investigate using an annonymous class for this so Suppliers are invisible and the
    // formula is inline on the instantiation of LinoleumFormula
    @SerialVersionUID(1L)
    private class HelloFormula extends Supplier[SscheckFormula] with Serializable { 
        def get(): SscheckFormula = {
            // FIXME eventually doesn´t work
            // now{ events: Letter =>
            //     val found = events.find{_.epochUnixNano < 0} 
            //     found must beSome
            // }
            always { events: Letter =>
                // FIXME idiomatic: events must contain(beLike{event: LinoleumEvent => event.epochUnixNano > 0 })
                events.find{_.epochUnixNano < 0} must beSome
            } during 1
        }
    }

    def main(args: Array[String]): Unit = {
        val formula = LinoleumFormula("Hello Linoleum", new HelloFormula())

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
        env.execute("hello spans")

        log.warn("Ending program")
        println("bye")
    }
}
