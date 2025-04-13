package es.ucm.fdi.demiourgoi.linoleum.ltlss

import com.google.protobuf.ByteString
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.slf4j.LoggerFactory

package object source {
  type SpanInfoStream = DataStream[SpanInfo]
}

package source {
  object ExportTraceServiceRequestProtoDeserializer {
    private val log = LoggerFactory.getLogger(ExportTraceServiceRequestProtoDeserializer.getClass.getName)
  }

  @SerialVersionUID(1L)
  class ExportTraceServiceRequestProtoDeserializer
    extends DeserializationSchema[ExportTraceServiceRequest] with Serializable {
    import ExportTraceServiceRequestProtoDeserializer._

    override def deserialize(bytes: Array[Byte]): ExportTraceServiceRequest = {
      val request = ExportTraceServiceRequest.parseFrom(bytes)
      log.debug("Parsed request {}", request)
      request
    }

    override def isEndOfStream(t: ExportTraceServiceRequest): Boolean = false

    override def getProducedType: TypeInformation[ExportTraceServiceRequest] =
      TypeInformation.of(classOf[ExportTraceServiceRequest])
  }
}

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