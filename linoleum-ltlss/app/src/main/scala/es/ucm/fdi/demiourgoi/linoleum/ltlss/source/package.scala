package es.ucm.fdi.demiourgoi.linoleum.ltlss

import com.google.protobuf.ByteString
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.slf4j.LoggerFactory
import java.{util=>jutil}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner

import  org.apache.flink.util.Collector
import scala.jdk.CollectionConverters._

package object source {
  type SpanInfoStream = DataStream[SpanInfo]
}
package source {

  import org.apache.flink.configuration.{Configuration, PipelineOptions}

  import java.time.Duration

  // TODO fields and YAML serde
  case class LinoleumConfig(
    localFlinkEnv: Boolean,
    kafkaBootstrapServers: String = "localhost:9092",
    kafkaTopics: String = "otlp_spans",
    kafkaGroupIdPrefix: String = "linolenum-cg",
    eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500)
  )

  object LinoleumSrc {
    private val log = LoggerFactory.getLogger(LinoleumSrc.getClass.getName)
    private val protoSerdeOption = "{type: kryo, kryo-type: registered, class: com.twitter.chill.protobuf.ProtobufSerializer}"

    def flinkEnv(linolenumCfg: LinoleumConfig): StreamExecutionEnvironment = {
      // https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/serialization/third_party_serializers/
      val config = new Configuration()
      addSerdeOptions(config)
      val env = if (linolenumCfg.localFlinkEnv) {
        // https://stackoverflow.com/questions/46988499/flink-webui-when-running-from-ide
        log.warn("Open Flink web UI at http://localhost:8081/#/overview")
        StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config)
      }
      else StreamExecutionEnvironment.getExecutionEnvironment(config)
      // Event time is now the default
      // https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/environment/StreamExecutionEnvironment.html#setStreamTimeCharacteristic-org.apache.flink.streaming.api.TimeCharacteristic-
      env
    }

    private def addSerdeOptions(config: Configuration): Configuration = {
      config.set(PipelineOptions.SERIALIZATION_CONFIG,
        List(
          s"io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest: $protoSerdeOption",
          s"es.ucm.fdi.demiourgoi.linoleum.ltlss.SpanInfo: $protoSerdeOption",
        ).asJava
      )
      config
    }
  }

  @SerialVersionUID(1L)
  class LinoleumSrc(@transient val cfg: LinoleumConfig)
    extends FlatMapFunction[ExportTraceServiceRequest, SpanInfo]
      with SerializableTimestampAssigner[SpanInfo]
      with Serializable {
    import LinoleumSrc._
    import messages._
    import evaluator.TimeUtils.nanosToMs

    def apply(env: StreamExecutionEnvironment): SpanInfoStream = {
      val kafkaSource = KafkaSource.builder[ExportTraceServiceRequest]()
        .setBootstrapServers(cfg.kafkaBootstrapServers)
        .setTopics(cfg.kafkaTopics)
        .setGroupId(s"${cfg.kafkaGroupIdPrefix}-${jutil.UUID.randomUUID()}")
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setValueOnlyDeserializer(new ExportTraceServiceRequestProtoDeserializer())
        .build()

      val exportTracesRequests = env.fromSource(kafkaSource,
        // no watermarks as we'll override this after extracting this per record
        WatermarkStrategy.forBoundedOutOfOrderness(cfg.eventsMaxOutOfOrderness),
        "exportTracesRequests")

      val spanInfos = exportTracesRequests.flatMap[SpanInfo](this)
      spanInfos.assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness(cfg.eventsMaxOutOfOrderness)
          // https://stackoverflow.com/questions/73825459/why-flink-1-15-2-showing-no-watermark-watermarks-are-only-available-if-eventtim
          .withIdleness(cfg.eventsMaxOutOfOrderness) // FIXME split into 2 settings, or rename
          .withTimestampAssigner(this)
      )
    }

    override def flatMap(exportTracesRequest: ExportTraceServiceRequest, out: Collector[SpanInfo]): Unit = {
      exportTracesRequest.getResourceSpansList.forEach{ resourceSpans =>
        val resource = resourceSpans.getResource
        val scopeSpansList = resourceSpans.getScopeSpansList
        log.debug("Received {} scope span lists for a resource", scopeSpansList.size())
        scopeSpansList.forEach{scopeSpan =>
          val spanList = scopeSpan.getSpansList
          val scope = scopeSpan.getScope
          log.debug("Received {} spans for instrumentation scope with name {}", spanList.size(), scope.getName)
          spanList.forEach{span =>
            val spanInfo = SpanInfo.newBuilder()
              .setSpan(span).setScope(scope).setScopeSchemaUrl(scopeSpan.getSchemaUrl)
              .setResource(resource).setResourceSchemaUrl(resourceSpans.getSchemaUrl)
              .build()
            log.debug("Received span with trace id {} and span id {}",
              spanInfo.hexTraceId, spanInfo.hexSpanId)
            out.collect(spanInfo)
          }
        }
      }
    }

    /** Timestamps can be an arbitrary long value, but all built-in implementations represent
     *  it as the milliseconds since the Epoch (midnight, January 1, 1970 UTC), the same way
     *  as System.currentTimeMillis() does it.*/
    override def extractTimestamp(spanInfo: SpanInfo, recordTimestamp: Long): Long =
      nanosToMs(spanInfo.getSpan.getStartTimeUnixNano)
  }

  object ExportTraceServiceRequestProtoDeserializer {
    private val log = LoggerFactory.getLogger(ExportTraceServiceRequestProtoDeserializer.getClass.getName)
  }
  @SerialVersionUID(1L)
  class ExportTraceServiceRequestProtoDeserializer
    extends DeserializationSchema[ExportTraceServiceRequest] with Serializable {
    import ExportTraceServiceRequestProtoDeserializer._

    override def deserialize(bytes: Array[Byte]): ExportTraceServiceRequest = {
      val request = ExportTraceServiceRequest.parseFrom(bytes)
      log.debug("Parsed request with {} spans", request.getResourceSpansCount)
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