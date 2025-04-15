package es.ucm.fdi.demiourgoi.linoleum.ltlss

import org.slf4j.LoggerFactory

object Main {
    import source._

    private val log = LoggerFactory.getLogger(Main.getClass.getName)

    def main(args: Array[String]): Unit = {
        log.warn("Starting program")
        val env = LinoleumSrc.flinkEnv()
        val linoleumSrc = new LinoleumSrc(LinoleumConfig())
        val spanInfos = linoleumSrc(env)

        spanInfos.print()
        env.execute("hello spans")

        log.warn("Ending program")
        println("bye")
    }
}
