package es.ucm.fdi.demiourgoi.linoleum.ltlss

import com.google.protobuf.ByteString
import org.apache.flink.streaming.api.datastream.DataStream

package object messages {
  implicit class LinoleumSpanInfo(self: SpanInfo) {
    val hexTraceId: String = byteString2HexString(self.getSpan.getTraceId)
    val hexSpanId: String = byteString2HexString(self.getSpan.getSpanId)
    /** Per https://opentelemetry.io/docs/concepts/signals/traces/ a root span,
     * "denoting the beginning and end of the entire operation [...]  has no parent_id"
     * which in https://github.com/open-telemetry/opentelemetry-proto/blob/4ca4f0335c63cda7ab31ea7ed70d6553aee14dce/opentelemetry/proto/trace/v1/trace.proto
     * is encoded in `bytes parent_span_id = 4;` that is not optional, as "If this is a root span, then this field
     * must be empty"
     * */
    val isRoot: Boolean = self.getSpan.getParentSpanId.isEmpty
  }
  // From https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
  def byteString2HexString(byteString: ByteString): String =
    byteString.toByteArray.map("%02X" format _).mkString.toLowerCase
}

package object sources {
  type SpanInfoStream = DataStream[SpanInfo]
}
package sources {
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
}
