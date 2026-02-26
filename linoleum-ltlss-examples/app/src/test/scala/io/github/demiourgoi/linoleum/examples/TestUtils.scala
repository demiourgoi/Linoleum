package io.github.demiourgoi.linoleum.examples

import java.nio.charset.Charset

import org.apache.flink.api.common.state.KeyedStateStore

import com.google.protobuf.ByteString

import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Span.Event
import io.github.demiourgoi.linoleum.messages.SpanInfo

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.apache.flink.api.common.state.{
  KeyedStateStore,
  ValueState,
  ValueStateDescriptor
}
import io.opentelemetry.proto.common.v1.AnyValue

package object Skips {
  def skipTest() = {
    println("Skipping test")
    true
  }
}

package object Stubs {
  def spanBuilderToSpanInfo(span: Span.Builder): SpanInfo =
    SpanInfo
      .newBuilder()
      .setSpan(span.build())
      .build()

  def stringAtribute(k: String, v: String): KeyValue =
    KeyValue
      .newBuilder()
      .setKey(k)
      .setValue(AnyValue.newBuilder().setStringValue(v).build())
      .build()

  def childSpan(parentSpanId: String): Span.Builder =
    Span
      .newBuilder()
      .setParentSpanId(
        ByteString.copyFrom(parentSpanId, Charset.forName("UTF-8"))
      )
}

package object Mocks {

  /** Return mocks for a clean state in which the state is not initialized */
  def cleanMocks() = {
    // Create mock for KeyedStateStore
    val mockStateStore = mock(classOf[KeyedStateStore])
    val mockValueState = mock(classOf[ValueState[String]])

    // Configure the mocks as specified
    when(mockStateStore.getState(any[ValueStateDescriptor[String]]()))
      .thenReturn(mockValueState)
    when(mockValueState.value()).thenReturn(null)
    doNothing().when(mockValueState).update(any[String]())
    new {
      val stateStore = mockStateStore
      val valueState = mockValueState
    }
  }
}
