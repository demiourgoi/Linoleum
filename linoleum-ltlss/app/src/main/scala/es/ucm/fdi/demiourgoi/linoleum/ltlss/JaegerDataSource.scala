package es.ucm.fdi.demiourgoi.linoleum.ltlss

import com.google.protobuf.ByteString
import org.apache.flink.streaming.api.datastream.DataStream

package object messages {
  implicit class LinoleumSpanInfo(self: SpanInfo) {
    val hexTraceId: String = byteString2HexString(self.getSpan.getTraceId)
    val hexSpanId: String = byteString2HexString(self.getSpan.getSpanId)
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
