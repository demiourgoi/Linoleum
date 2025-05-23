// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: opentelemetry/proto/trace/v1/trace.proto

package io.opentelemetry.proto.trace.v1;

public interface SpanOrBuilder extends
    // @@protoc_insertion_point(interface_extends:opentelemetry.proto.trace.v1.Span)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * A unique identifier for a trace. All spans from the same trace share
   * the same `trace_id`. The ID is a 16-byte array. An ID with all zeroes OR
   * of length other than 16 bytes is considered invalid (empty string in OTLP/JSON
   * is zero-length and thus is also invalid).
   * This field is required.
   * </pre>
   *
   * <code>bytes trace_id = 1;</code>
   * @return The traceId.
   */
  com.google.protobuf.ByteString getTraceId();

  /**
   * <pre>
   * A unique identifier for a span within a trace, assigned when the span
   * is created. The ID is an 8-byte array. An ID with all zeroes OR of length
   * other than 8 bytes is considered invalid (empty string in OTLP/JSON
   * is zero-length and thus is also invalid).
   * This field is required.
   * </pre>
   *
   * <code>bytes span_id = 2;</code>
   * @return The spanId.
   */
  com.google.protobuf.ByteString getSpanId();

  /**
   * <pre>
   * trace_state conveys information about request position in multiple distributed tracing graphs.
   * It is a trace_state in w3c-trace-context format: https://www.w3.org/TR/trace-context/#tracestate-header
   * See also https://github.com/w3c/distributed-tracing for more details about this field.
   * </pre>
   *
   * <code>string trace_state = 3;</code>
   * @return The traceState.
   */
  java.lang.String getTraceState();
  /**
   * <pre>
   * trace_state conveys information about request position in multiple distributed tracing graphs.
   * It is a trace_state in w3c-trace-context format: https://www.w3.org/TR/trace-context/#tracestate-header
   * See also https://github.com/w3c/distributed-tracing for more details about this field.
   * </pre>
   *
   * <code>string trace_state = 3;</code>
   * @return The bytes for traceState.
   */
  com.google.protobuf.ByteString
      getTraceStateBytes();

  /**
   * <pre>
   * The `span_id` of this span's parent span. If this is a root span, then this
   * field must be empty. The ID is an 8-byte array.
   * </pre>
   *
   * <code>bytes parent_span_id = 4;</code>
   * @return The parentSpanId.
   */
  com.google.protobuf.ByteString getParentSpanId();

  /**
   * <pre>
   * Flags, a bit field. 8 least significant bits are the trace
   * flags as defined in W3C Trace Context specification. Readers
   * MUST not assume that 24 most significant bits will be zero.
   * To read the 8-bit W3C trace flag, use `flags &amp; SPAN_FLAGS_TRACE_FLAGS_MASK`.
   * When creating span messages, if the message is logically forwarded from another source
   * with an equivalent flags fields (i.e., usually another OTLP span message), the field SHOULD
   * be copied as-is. If creating from a source that does not have an equivalent flags field
   * (such as a runtime representation of an OpenTelemetry span), the high 24 bits MUST
   * be set to zero.
   * [Optional].
   * See https://www.w3.org/TR/trace-context-2/#trace-flags for the flag definitions.
   * </pre>
   *
   * <code>fixed32 flags = 16;</code>
   * @return The flags.
   */
  int getFlags();

  /**
   * <pre>
   * A description of the span's operation.
   * For example, the name can be a qualified method name or a file name
   * and a line number where the operation is called. A best practice is to use
   * the same display name at the same call point in an application.
   * This makes it easier to correlate spans in different traces.
   * This field is semantically required to be set to non-empty string.
   * Empty value is equivalent to an unknown span name.
   * This field is required.
   * </pre>
   *
   * <code>string name = 5;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * A description of the span's operation.
   * For example, the name can be a qualified method name or a file name
   * and a line number where the operation is called. A best practice is to use
   * the same display name at the same call point in an application.
   * This makes it easier to correlate spans in different traces.
   * This field is semantically required to be set to non-empty string.
   * Empty value is equivalent to an unknown span name.
   * This field is required.
   * </pre>
   *
   * <code>string name = 5;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <pre>
   * Distinguishes between spans generated in a particular context. For example,
   * two spans with the same name may be distinguished using `CLIENT` (caller)
   * and `SERVER` (callee) to identify queueing latency associated with the span.
   * </pre>
   *
   * <code>.opentelemetry.proto.trace.v1.Span.SpanKind kind = 6;</code>
   * @return The enum numeric value on the wire for kind.
   */
  int getKindValue();
  /**
   * <pre>
   * Distinguishes between spans generated in a particular context. For example,
   * two spans with the same name may be distinguished using `CLIENT` (caller)
   * and `SERVER` (callee) to identify queueing latency associated with the span.
   * </pre>
   *
   * <code>.opentelemetry.proto.trace.v1.Span.SpanKind kind = 6;</code>
   * @return The kind.
   */
  io.opentelemetry.proto.trace.v1.Span.SpanKind getKind();

  /**
   * <pre>
   * start_time_unix_nano is the start time of the span. On the client side, this is the time
   * kept by the local machine where the span execution starts. On the server side, this
   * is the time when the server's application handler starts running.
   * Value is UNIX Epoch time in nanoseconds since 00:00:00 UTC on 1 January 1970.
   * This field is semantically required and it is expected that end_time &gt;= start_time.
   * </pre>
   *
   * <code>fixed64 start_time_unix_nano = 7;</code>
   * @return The startTimeUnixNano.
   */
  long getStartTimeUnixNano();

  /**
   * <pre>
   * end_time_unix_nano is the end time of the span. On the client side, this is the time
   * kept by the local machine where the span execution ends. On the server side, this
   * is the time when the server application handler stops running.
   * Value is UNIX Epoch time in nanoseconds since 00:00:00 UTC on 1 January 1970.
   * This field is semantically required and it is expected that end_time &gt;= start_time.
   * </pre>
   *
   * <code>fixed64 end_time_unix_nano = 8;</code>
   * @return The endTimeUnixNano.
   */
  long getEndTimeUnixNano();

  /**
   * <pre>
   * attributes is a collection of key/value pairs. Note, global attributes
   * like server name can be set using the resource API. Examples of attributes:
   *     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
   *     "/http/server_latency": 300
   *     "example.com/myattribute": true
   *     "example.com/score": 10.239
   * The OpenTelemetry API specification further restricts the allowed value types:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
   * Attribute keys MUST be unique (it is not allowed to have more than one
   * attribute with the same key).
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.common.v1.KeyValue attributes = 9;</code>
   */
  java.util.List<io.opentelemetry.proto.common.v1.KeyValue> 
      getAttributesList();
  /**
   * <pre>
   * attributes is a collection of key/value pairs. Note, global attributes
   * like server name can be set using the resource API. Examples of attributes:
   *     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
   *     "/http/server_latency": 300
   *     "example.com/myattribute": true
   *     "example.com/score": 10.239
   * The OpenTelemetry API specification further restricts the allowed value types:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
   * Attribute keys MUST be unique (it is not allowed to have more than one
   * attribute with the same key).
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.common.v1.KeyValue attributes = 9;</code>
   */
  io.opentelemetry.proto.common.v1.KeyValue getAttributes(int index);
  /**
   * <pre>
   * attributes is a collection of key/value pairs. Note, global attributes
   * like server name can be set using the resource API. Examples of attributes:
   *     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
   *     "/http/server_latency": 300
   *     "example.com/myattribute": true
   *     "example.com/score": 10.239
   * The OpenTelemetry API specification further restricts the allowed value types:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
   * Attribute keys MUST be unique (it is not allowed to have more than one
   * attribute with the same key).
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.common.v1.KeyValue attributes = 9;</code>
   */
  int getAttributesCount();
  /**
   * <pre>
   * attributes is a collection of key/value pairs. Note, global attributes
   * like server name can be set using the resource API. Examples of attributes:
   *     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
   *     "/http/server_latency": 300
   *     "example.com/myattribute": true
   *     "example.com/score": 10.239
   * The OpenTelemetry API specification further restricts the allowed value types:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
   * Attribute keys MUST be unique (it is not allowed to have more than one
   * attribute with the same key).
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.common.v1.KeyValue attributes = 9;</code>
   */
  java.util.List<? extends io.opentelemetry.proto.common.v1.KeyValueOrBuilder> 
      getAttributesOrBuilderList();
  /**
   * <pre>
   * attributes is a collection of key/value pairs. Note, global attributes
   * like server name can be set using the resource API. Examples of attributes:
   *     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
   *     "/http/server_latency": 300
   *     "example.com/myattribute": true
   *     "example.com/score": 10.239
   * The OpenTelemetry API specification further restricts the allowed value types:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
   * Attribute keys MUST be unique (it is not allowed to have more than one
   * attribute with the same key).
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.common.v1.KeyValue attributes = 9;</code>
   */
  io.opentelemetry.proto.common.v1.KeyValueOrBuilder getAttributesOrBuilder(
      int index);

  /**
   * <pre>
   * dropped_attributes_count is the number of attributes that were discarded. Attributes
   * can be discarded because their keys are too long or because there are too many
   * attributes. If this value is 0, then no attributes were dropped.
   * </pre>
   *
   * <code>uint32 dropped_attributes_count = 10;</code>
   * @return The droppedAttributesCount.
   */
  int getDroppedAttributesCount();

  /**
   * <pre>
   * events is a collection of Event items.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Event events = 11;</code>
   */
  java.util.List<io.opentelemetry.proto.trace.v1.Span.Event> 
      getEventsList();
  /**
   * <pre>
   * events is a collection of Event items.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Event events = 11;</code>
   */
  io.opentelemetry.proto.trace.v1.Span.Event getEvents(int index);
  /**
   * <pre>
   * events is a collection of Event items.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Event events = 11;</code>
   */
  int getEventsCount();
  /**
   * <pre>
   * events is a collection of Event items.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Event events = 11;</code>
   */
  java.util.List<? extends io.opentelemetry.proto.trace.v1.Span.EventOrBuilder> 
      getEventsOrBuilderList();
  /**
   * <pre>
   * events is a collection of Event items.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Event events = 11;</code>
   */
  io.opentelemetry.proto.trace.v1.Span.EventOrBuilder getEventsOrBuilder(
      int index);

  /**
   * <pre>
   * dropped_events_count is the number of dropped events. If the value is 0, then no
   * events were dropped.
   * </pre>
   *
   * <code>uint32 dropped_events_count = 12;</code>
   * @return The droppedEventsCount.
   */
  int getDroppedEventsCount();

  /**
   * <pre>
   * links is a collection of Links, which are references from this span to a span
   * in the same or different trace.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Link links = 13;</code>
   */
  java.util.List<io.opentelemetry.proto.trace.v1.Span.Link> 
      getLinksList();
  /**
   * <pre>
   * links is a collection of Links, which are references from this span to a span
   * in the same or different trace.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Link links = 13;</code>
   */
  io.opentelemetry.proto.trace.v1.Span.Link getLinks(int index);
  /**
   * <pre>
   * links is a collection of Links, which are references from this span to a span
   * in the same or different trace.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Link links = 13;</code>
   */
  int getLinksCount();
  /**
   * <pre>
   * links is a collection of Links, which are references from this span to a span
   * in the same or different trace.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Link links = 13;</code>
   */
  java.util.List<? extends io.opentelemetry.proto.trace.v1.Span.LinkOrBuilder> 
      getLinksOrBuilderList();
  /**
   * <pre>
   * links is a collection of Links, which are references from this span to a span
   * in the same or different trace.
   * </pre>
   *
   * <code>repeated .opentelemetry.proto.trace.v1.Span.Link links = 13;</code>
   */
  io.opentelemetry.proto.trace.v1.Span.LinkOrBuilder getLinksOrBuilder(
      int index);

  /**
   * <pre>
   * dropped_links_count is the number of dropped links after the maximum size was
   * enforced. If this value is 0, then no links were dropped.
   * </pre>
   *
   * <code>uint32 dropped_links_count = 14;</code>
   * @return The droppedLinksCount.
   */
  int getDroppedLinksCount();

  /**
   * <pre>
   * An optional final status for this span. Semantically when Status isn't set, it means
   * span's status code is unset, i.e. assume STATUS_CODE_UNSET (code = 0).
   * </pre>
   *
   * <code>.opentelemetry.proto.trace.v1.Status status = 15;</code>
   * @return Whether the status field is set.
   */
  boolean hasStatus();
  /**
   * <pre>
   * An optional final status for this span. Semantically when Status isn't set, it means
   * span's status code is unset, i.e. assume STATUS_CODE_UNSET (code = 0).
   * </pre>
   *
   * <code>.opentelemetry.proto.trace.v1.Status status = 15;</code>
   * @return The status.
   */
  io.opentelemetry.proto.trace.v1.Status getStatus();
  /**
   * <pre>
   * An optional final status for this span. Semantically when Status isn't set, it means
   * span's status code is unset, i.e. assume STATUS_CODE_UNSET (code = 0).
   * </pre>
   *
   * <code>.opentelemetry.proto.trace.v1.Status status = 15;</code>
   */
  io.opentelemetry.proto.trace.v1.StatusOrBuilder getStatusOrBuilder();
}
