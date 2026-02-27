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

// for i in $(seq 10); do ( echo "run $i" && ./gradlew test --tests "io.github.demiourgoi.linoleum.examples.MaudeLotrBombadilLivenessTest" --rerun-tasks ) || break; done
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
                  "[{\"text\": \"I completely agree! Tom Bombadil's enigmatic nature is part of what makes him so fascinating. His origins and true identity are not fully explained in \\\"The Lord of the Rings,\\\" which has led to much speculation and debate among fans and scholars alike. Some theories suggest that he might be an embodiment of the spirit of the land, or perhaps an ancient being who predates the creation of Middle-earth as we know it.\\n\\nOne of the most intriguing aspects of Tom Bombadil is his relationship with the One Ring. Unlike other characters, the Ring has no power over him, and he remains indifferent to it. This indifference is a stark contrast to the way other characters, even the most powerful ones like Sauron and Gandalf, are affected by the Ring. This unique trait adds to the mystery surrounding Tom Bombadil and raises questions about his true nature and role in the grand scheme of Middle-earth.\\n\\nWould you like to explore some of the theories about Tom Bombadil's origins and significance, or perhaps discuss his role in the story and how he interacts with other characters?\"}]"
                )
              ).asJava
            )
            .build()
        ).asJava
      )

    spanBuilderToSpanInfo(span)
  }

  def midTurnNoMsgSpan = {
    val span = childSpan("11cd568c4e4110c2")
      .setName("Whatever")
    spanBuilderToSpanInfo(span)
  }

  "For the MaudeLotrBombadilLiveness example" >> {
    import maudeLotrBombadilLivenessMonitor._

    "The rule [span-end-no-rage-no-end-turn] applies as expected when rage is pending" >> {
      if (!MistralClient.isMistralApiKeyAvailableOnEnv()) {
        skipTest()
        ok
      } else {
        val orderedEvents = List(
          SpanStart(bombadilSpan),
          SpanEnd(midTurnNoRageSpan),
          SpanEnd(midTurnNoMsgSpan)
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
            ),
            // [span-end-no-msg-no-end-turn] applied
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
