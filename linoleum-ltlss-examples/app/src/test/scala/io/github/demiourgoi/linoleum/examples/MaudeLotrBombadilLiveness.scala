package io.github.demiourgoi.linoleum.examples

import java.time.Duration

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.jdk.CollectionConverters._

import io.opentelemetry.proto.common.v1.{AnyValue, KeyValue}
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Span.Event

import io.github.demiourgoi.linoleum._
import io.github.demiourgoi.linoleum.messages._
import io.github.demiourgoi.linoleum.maude._

import Mocks._
import Stubs._
import Skips._
import net.bytebuddy.dynamic.loading.PackageDefinitionStrategy.Definition.Undefined
import scalaz.std.string

@RunWith(classOf[JUnitRunner])
class MaudeLotrBombadilLivenessTest extends org.specs2.mutable.Specification {
  def bombadilSpan = {
    val userMsgEvent = Event
      .newBuilder()
      .setName("gen_ai.user.message")
      .addAllAttributes(
        List(
          stringAtribute(
            "content",
            "[{\"text\": \"I like the Elves, but Tom Bombadil is the best\"}]"
          )
        ).asJava
      )
      .build()
    // this is a root span because not parentSpanId is specified
    val span = Span
      .newBuilder()
      .addAllEvents(List(userMsgEvent).asJava)
    spanBuilderToSpanInfo(span)
  }

  def midTurnNoRageSpan = {
    val span = childSpan("11cd568c4e4110c2")
      .setName("chat")
      .addAllAttributes(
        List(
          stringAtribute("gen_ai.operation.name", "chat")
        ).asJava
      )
      .addAllEvents(
        List(
          Event
            .newBuilder()
            .setName("gen_ai.choice")
            .addAllAttributes(
              List(
                stringAtribute(
                  "message",
                  "[{\"text\": \"I'm glad to hear you're interested in the Elves and Tom Bombadil! They are indeed fascinating aspects of Middle-earth. What specific questions or topics about the Elves or Tom Bombadil would you like to discuss?\"}]"
                )
              ).asJava
            )
            .build()
        ).asJava
      )

    spanBuilderToSpanInfo(span)
  }

  // FIXME: disable if no creadentails
  "For the MaudeLotrBombadilLiveness example" >> {
    import maudeLotrBombadilLivenessMonitor._

    "The rule [span-end-no-rage-no-end-turn] applies as expected" >> {
      if (!MistralClient.isMistralApiKeyAvailableOnEnv()) {
        skipTest()
        ok
      } else {
        val orderedEvents = List(
          SpanStart(bombadilSpan),
          SpanEnd(midTurnNoRageSpan)
        )
        println("orderedEvents:")
        orderedEvents.foreach { ev => println(ev.toMaude("oid")) }

        val mocks = cleanMocks()

        val (truthValue, soups) =
          PropertyInstances.MaudeMonitorProperty.evaluateWithSteps(monitor)(
            "fooKey",
            mocks.stateStore,
            orderedEvents
          )

        println(s"soups: $soups")

        (truthValue === Undecided) and (
          soups === List(
            // [span-start-first-bombadil] applied
            (
              s"""< $monOid : Monitor | isRagePending : true, turnsToRage : 5 >""",
              Undecided
            ),
            // [span-end-no-rage-no-end-turn] applied
            (
              s"""< $monOid : Monitor | isRagePending : true, turnsToRage : 5 >""",
              Undecided
            )
          )
        )

        ok
      }
    }
  }
}
