package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._
import com.google.protobuf.Timestamp
import io.grpc.{Channel, ManagedChannelBuilder}
import io.jaegertracing.api_v3.{QueryServiceGrpc, QueryServiceOuterClass}
import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters._

object TimeUtils {
    def instantToTimestamp(instant: Instant): Timestamp =
        Timestamp.newBuilder()
          .setSeconds(instant.getEpochSecond)
          .setNanos(instant.getNano)
          .build()

    def timestampToInstant(timestamp: Timestamp): Instant =
        Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos)
}

object Main extends App {
    import TimeUtils._

    val (host, port) = ("localhost", 16685)
    val channel: Channel = {
        val builder = ManagedChannelBuilder.forAddress(host, port)
        builder.usePlaintext() // FIXME configurable, local jaeger is htt
        builder.build()
    }
    val client = QueryServiceGrpc.newBlockingStub(channel)
    val now = Instant.now()

    // https://github.com/jaegertracing/jaeger-idl/blob/main/proto/api_v3/query_service.proto
    val request = QueryServiceOuterClass.GetTraceRequest.newBuilder()
      .setTraceId("adcd471c2419a12256c99086474f0e1b").build()
    val tracesData = client.getTrace(request)
    tracesData forEachRemaining  { response =>
        println(response)
    }

    val findTracesRequest = QueryServiceOuterClass.FindTracesRequest.newBuilder()
      .setQuery(
          QueryServiceOuterClass.TraceQueryParameters.newBuilder()
            .setServiceName("linoleum.tools.simreplayer:kotlin")
            .setStartTimeMin(
               instantToTimestamp(now.minus(Duration.ofMinutes(10)))
            )
            .setStartTimeMax(instantToTimestamp(now))
            .build()
      ).build()
    val searchResponse = client.findTraces(findTracesRequest).asScala.toList
    println(s"Found ${searchResponse.size} traces")
    searchResponse foreach { response =>
        println(response)
    }

    val formula = always{ x: Int => x must be_>(0) } during 10
    Console.println(s"Hello ${formula}")
}
