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
import org.apache.flink.api.common.state.{KeyedStateStore, ValueState, ValueStateDescriptor}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

@RunWith(classOf[JUnitRunner])
class MaudeLotrImageGenSafetyTest extends org.specs2.mutable.Specification {
  def spanBuilderToSpanInfo(span: Span.Builder): SpanInfo =
    SpanInfo
      .newBuilder()
      .setSpan(span.build())
      .build()

  def nonImageSpan = spanBuilderToSpanInfo(Span.newBuilder())

  def imageSpan(durationNanos: Long): SpanInfo = {
    val attributes = List(
      KeyValue
        .newBuilder()
        .setKey("gen_ai.operation.name")
        .setValue(AnyValue.newBuilder().setStringValue("execute_tool").build())
        .build(),
      KeyValue
        .newBuilder()
        .setKey("gen_ai.tool.name")
        .setValue(
          AnyValue.newBuilder().setStringValue("generate_image").build()
        )
        .build()
    )

    val span = Span
      .newBuilder()
      .addAllAttributes(attributes.asJava)
      .setStartTimeUnixNano(0)
      .setEndTimeUnixNano(durationNanos)

    spanBuilderToSpanInfo(span)
  }

  "For the MaudeLotrImageGenSafety example" >> {
    "we can track image generation time using the monitor" >> {
      import maudeLotrImageGenSafetyMonitor._

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

      // Create mock for KeyedStateStore
      val mockStateStore = mock(classOf[KeyedStateStore])
      val mockValueState = mock(classOf[ValueState[String]])

      // Configure the mocks as specified
      when(mockStateStore.getState(any[ValueStateDescriptor[String]]())).thenReturn(mockValueState)
      when(mockValueState.value()).thenReturn(null)
      doNothing().when(mockValueState).update(any[String]())

      for (_ <- 1 to 3) {
        val (truthValue, soups) =
          PropertyInstances.MaudeMonitorProperty.evaluateWithSteps(monitor)(
            "fooKey", mockStateStore, orderedEvents
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
