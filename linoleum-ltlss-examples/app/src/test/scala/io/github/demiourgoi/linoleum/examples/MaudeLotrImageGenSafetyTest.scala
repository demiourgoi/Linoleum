package io.github.demiourgoi.linoleum.examples

import java.time.Duration

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.jdk.CollectionConverters._

import io.opentelemetry.proto.common.v1.{AnyValue, KeyValue}
import io.opentelemetry.proto.trace.v1.Span

import io.github.demiourgoi.linoleum._
import io.github.demiourgoi.linoleum.messages._
import io.github.demiourgoi.linoleum.maude._

import Mocks._
import Stubs._

@RunWith(classOf[JUnitRunner])
class MaudeLotrImageGenSafetyTest extends org.specs2.mutable.Specification {

  def nonImageSpan = spanBuilderToSpanInfo(Span.newBuilder())

  def imageSpan(durationNanos: Long): SpanInfo = {
    val attributes = List(
      stringAtribute("gen_ai.operation.name", "execute_tool"),
      stringAtribute("gen_ai.tool.name", "generate_image")
    )

    val span = Span
      .newBuilder()
      .addAllAttributes(attributes.asJava)
      .setStartTimeUnixNano(0)
      .setEndTimeUnixNano(durationNanos)

    spanBuilderToSpanInfo(span)
  }

  "For the MaudeLotrImageGenSafety example" >> {
    import maudeLotrImageGenSafetyMonitor._

    "we can track image generation time using the monitor" >> {
      val orderedEvents = List(
        SpanStart(nonImageSpan), // does nothing
        SpanEnd(nonImageSpan), // Non image span end does nothing
        SpanEnd(imageSpan(10)), // image span end increases time count
        SpanStart(
          imageSpan(10)
        ), // image span start does not increase time count
        SpanEnd(
          imageSpan(3000000000L)
        ) // image span end increases time count again
      )
      println("orderedEvents:")
      orderedEvents.foreach { ev => println(ev.toMaude("oid")) }

      val mocks = cleanMocks()

      for (_ <- 1 to 3) {
        val (truthValue, soups) =
          PropertyInstances.MaudeMonitorProperty.evaluateWithSteps(monitor)(
            "fooKey",
            mocks.stateStore,
            orderedEvents
          )
        (truthValue === False) and (
          soups === List(
            (s"""< $monOid : Monitor | timeSpentOnImageGenNanos : 0 >""", True),
            (s"""< $monOid : Monitor | timeSpentOnImageGenNanos : 0 >""", True),
            (
              s"""< $monOid : Monitor | timeSpentOnImageGenNanos : 10 >""",
              True
            ),
            (
              s"""< $monOid : Monitor | timeSpentOnImageGenNanos : 10 >""",
              True
            ),
            (
              s"""< $monOid : Monitor | timeSpentOnImageGenNanos : 3000000010 >""",
              False
            )
          )
        )
      }

      ok
    }
  }

}
