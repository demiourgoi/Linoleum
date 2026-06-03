package io.github.demiourgoi.linoleum.loadgen

import com.google.protobuf.ByteString
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.{AnyValue, InstrumentationScope, KeyValue}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.{ResourceSpans, ScopeSpans, Span}

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/** Generates realistic OTEL ExportTraceServiceRequest protobuf messages
  * matching the span structure seen in representative traces.
  *
  * Each request contains one trace with 4 spans arranged as:
  *   root: invoke_agent (parentSpanId empty)
  *     ├── execute_event_loop_cycle
  *           ├── chat (with gen_ai events)
  *           └── stream_chat
  *
  * Span names, attributes, and event structure mirror the lotrbot agent traces
  * found in maude_terms.maudes.
  */
object SpanGenerator {
  private val rng = new SecureRandom()

  // Counter for generating unique chat IDs
  private val chatIdCounter = new AtomicLong(
    System.currentTimeMillis() / 1000
  )

  /** Generate one ExportTraceServiceRequest containing a single realistic trace.
    * Uses the current wall-clock time so Flink's watermark doesn't discard the spans.
    */
  def generateRequest(): ExportTraceServiceRequest = {
    val traceId = randomBytes(16)
    val rootSpanId = randomBytes(8)
    val loopSpanId = randomBytes(8)
    val chatSpanId = randomBytes(8)
    val streamSpanId = randomBytes(8)

    // Use current time as the base; Flink's bounded out-of-orderness is 0.5s.
    // Spans need timestamps close to now or the watermark discards them.
    val baseTime = System.currentTimeMillis() * 1_000_000L
    val chatId = s"lotrbot/${java.util.UUID.randomUUID()}/${chatIdCounter.getAndIncrement()}"

    ExportTraceServiceRequest
      .newBuilder()
      .addResourceSpans(
        ResourceSpans
          .newBuilder()
          .setResource(
            Resource
              .newBuilder()
              .addAttributes(kv("service.name", "lotrbot"))
              .addAttributes(kv("telemetry.sdk.language", "python"))
              .addAttributes(kv("telemetry.sdk.name", "opentelemetry"))
              .build()
          )
          .addScopeSpans(
            ScopeSpans
              .newBuilder()
              .setScope(
                InstrumentationScope
                  .newBuilder()
                  .setName("strands-agents")
                  .setVersion("0.1.0")
                  .build()
              )
              // root span: invoke_agent
              .addSpans(buildRootSpan(traceId, rootSpanId, chatId, baseTime))
              // child 1: execute_event_loop_cycle
              .addSpans(buildLoopSpan(traceId, loopSpanId, rootSpanId, chatId, baseTime))
              // child 2: chat (nested under loop)
              .addSpans(buildChatSpan(traceId, chatSpanId, loopSpanId, chatId, baseTime))
              // child 3: stream_chat (nested under loop)
              .addSpans(buildStreamChatSpan(traceId, streamSpanId, loopSpanId, chatId, baseTime))
              .build()
          )
          .build()
      )
      .build()
  }

  private def buildRootSpan(
      traceId: ByteString,
      spanId: ByteString,
      chatId: String,
      baseTime: Long
  ): Span = {
    val start = baseTime
    val end = baseTime + randomDuration(1_000_000_000L, 3_000_000_000L) // 1-3s

    Span
      .newBuilder()
      .setTraceId(traceId)
      .setSpanId(spanId)
      .setParentSpanId(ByteString.EMPTY) // root span
      .setName(s"invoke_agent $chatId")
      .setKind(Span.SpanKind.SPAN_KIND_INTERNAL)
      .setStartTimeUnixNano(start)
      .setEndTimeUnixNano(end)
      .addAttributes(kv("lotrbot.chat_id", chatId))
      .addAttributes(kv("gen_ai.operation.name", "invoke_agent"))
      .addAttributes(kv("gen_ai.system", "strands-agents"))
      .addAttributes(kv("gen_ai.agent.name", chatId))
      .addAttributes(kv("gen_ai.request.model", "mistral-small-latest"))
      .addAttributes(
        kv(
          "gen_ai.agent.tools",
          """["generate_image", "say_something_nice"]"""
        )
      )
      .addAttributes(
        kv(
          "system_prompt",
          "\nYou are an expert in The Lord of the Rings (LOTR) universe.\n" +
            "You love all books and characters described in J. R. R. Tolkien novels, and related movies.\n" +
            "You are extremely knowledgeable about the LOTR universe, both from the books, movies, and TV shows.\n"
        )
      )
      // root span has user message + choice events
      .addEvents(
        Span.Event
          .newBuilder()
          .setTimeUnixNano(start + randomDuration(10_000_000L, 50_000_000L))
          .setName("gen_ai.user.message")
          .addAttributes(
            kv("content", """[{"text": "Tell me about the Balrog"}]""")
          )
          .build()
      )
      .addEvents(
        Span.Event
          .newBuilder()
          .setTimeUnixNano(end - randomDuration(10_000_000L, 50_000_000L))
          .setName("gen_ai.choice")
          .addAttributes(kv("finish_reason", "end_turn"))
          .addAttributes(
            kv(
              "message",
              """[{"text": "Ah, the Balrog... a fascinating and terrifying creature of Middle-earth!"}]"""
            )
          )
          .build()
      )
      .build()
  }

  private def buildLoopSpan(
      traceId: ByteString,
      spanId: ByteString,
      parentSpanId: ByteString,
      chatId: String,
      baseTime: Long
  ): Span = {
    val start = baseTime + randomDuration(10_000_000L, 50_000_000L)
    val end = baseTime + randomDuration(2_000_000_000L, 5_000_000_000L)

    Span
      .newBuilder()
      .setTraceId(traceId)
      .setSpanId(spanId)
      .setParentSpanId(parentSpanId)
      .setName("execute_event_loop_cycle")
      .setKind(Span.SpanKind.SPAN_KIND_INTERNAL)
      .setStartTimeUnixNano(start)
      .setEndTimeUnixNano(end)
      .addAttributes(kv("lotrbot.chat_id", chatId))
      .addAttributes(
        kv("event_loop.cycle_id", java.util.UUID.randomUUID().toString)
      )
      .build()
  }

  private def buildChatSpan(
      traceId: ByteString,
      spanId: ByteString,
      parentSpanId: ByteString,
      chatId: String,
      baseTime: Long
  ): Span = {
    val start = baseTime + randomDuration(50_000_000L, 100_000_000L)
    val end = start + randomDuration(500_000_000L, 2_000_000_000L)

    Span
      .newBuilder()
      .setTraceId(traceId)
      .setSpanId(spanId)
      .setParentSpanId(parentSpanId)
      .setName("chat")
      .setKind(Span.SpanKind.SPAN_KIND_INTERNAL)
      .setStartTimeUnixNano(start)
      .setEndTimeUnixNano(end)
      .addAttributes(kv("lotrbot.chat_id", chatId))
      .addAttributes(kv("gen_ai.operation.name", "chat"))
      .addAttributes(kv("gen_ai.system", "strands-agents"))
      .addAttributes(kv("gen_ai.request.model", "mistral-small-latest"))
      .addEvents(
        Span.Event
          .newBuilder()
          .setTimeUnixNano(start + randomDuration(10_000_000L, 30_000_000L))
          .setName("gen_ai.user.message")
          .addAttributes(
            kv("content", """[{"text": "Tell me about the Balrog"}]""")
          )
          .build()
      )
      .addEvents(
        Span.Event
          .newBuilder()
          .setTimeUnixNano(end - randomDuration(10_000_000L, 30_000_000L))
          .setName("gen_ai.choice")
          .addAttributes(kv("finish_reason", "end_turn"))
          .addAttributes(
            kv(
              "message",
              """[{"text": "The Balrog is a creature of shadow and flame..."}]"""
            )
          )
          .build()
      )
      .build()
  }

  private def buildStreamChatSpan(
      traceId: ByteString,
      spanId: ByteString,
      parentSpanId: ByteString,
      chatId: String,
      baseTime: Long
  ): Span = {
    val start = baseTime + randomDuration(50_000_000L, 100_000_000L)
    val end = start + randomDuration(500_000_000L, 2_000_000_000L)

    Span
      .newBuilder()
      .setTraceId(traceId)
      .setSpanId(spanId)
      .setParentSpanId(parentSpanId)
      .setName("stream_chat")
      .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
      .setStartTimeUnixNano(start)
      .setEndTimeUnixNano(end)
      .addAttributes(kv("lotrbot.chat_id", chatId))
      .addAttributes(kv("agent.trace.public", ""))
      .addAttributes(kv("http.request.method", "POST"))
      .addAttributes(
        kv("http.url", "https://api.mistral.ai/v1/chat/completions#stream")
      )
      .addAttributes(kv("server.address", "api.mistral.ai"))
      .addAttributes(kv("server.port", "443"))
      .addAttributes(kv("gen_ai.request.model", "mistral-small-latest"))
      .build()
  }

  // --- helpers ---

  private def randomBytes(n: Int): ByteString = {
    val bytes = new Array[Byte](n)
    rng.nextBytes(bytes)
    ByteString.copyFrom(bytes)
  }

  private def randomDuration(min: Long, max: Long): Long =
    min + (rng.nextLong() & Long.MaxValue) % (max - min + 1)

  private def kv(key: String, value: String): KeyValue =
    KeyValue
      .newBuilder()
      .setKey(key)
      .setValue(AnyValue.newBuilder().setStringValue(value).build())
      .build()
}
