package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._

object Main extends App {
    val formula = always{ x: Int => x must be_>(0) } during 10
    Console.println(s"Hello ${formula}")
}
