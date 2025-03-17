package es.ucm.fdi.demiourgoi.linoleum.ltlss

import java.util
import org.apache.flink.api.connector.source.SourceOutput
import io.opentelemetry.proto.trace.v1.{TracesData, Span}
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.common.v1.InstrumentationScope
import com.google.protobuf.ByteString

/**
 * A SpanInfo is a span paired with all the context information associated to it on
 * a parent TracesData message returned in a stream in Jaeger's response to a call to `FindTraces`
 *
 * @param resourceSchemaUrl: corresponds to the field schema_url in the parent ResourceSpans message
 * @param spansSchemaUrl:  corresponds to the field schema_url in the parent ScopeSpans message
 *
 * See the source proto schemas at:
 * - https://github.com/jaegertracing/jaeger-idl/blob/main/proto/api_v3/query_service.proto
 * - https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
 *
 * TODO: define as proto and keep hexTraceId and hexSpanId as static functions
 * */
case class SpanInfo(span: Span,
                    scope: InstrumentationScope, spansSchemaUrl: String,
                    resource: Resource, resourceSchemaUrl: String) {
  import SpanInfo._
  val hexTraceId: String = byteString2HexString(span.getTraceId)
  val hexSpanId: String = byteString2HexString(span.getSpanId)
}
object SpanInfo {
  // From https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
  private def byteString2HexString(byteString: ByteString): String =
    byteString.toByteArray.map("%02X" format _).mkString.toLowerCase
}

// https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/sources/
// Note the rich source function API is deprecated
/**
 * Data source that queries Jaeger for Span data using a find traces query determined by
 * the data source parameters
 *
 * The io.opentelemetry.proto.trace.v1.TracesData
 * */
class JaegerDataSource {
}

