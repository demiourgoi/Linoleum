package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._
import com.google.protobuf.Timestamp
import io.grpc.{Channel, ManagedChannelBuilder}
import io.jaegertracing.api_v3.{QueryServiceGrpc, QueryServiceOuterClass}

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters._
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.util.Collector

object TimeUtils {
    def instantToTimestamp(instant: Instant): Timestamp =
        Timestamp.newBuilder()
          .setSeconds(instant.getEpochSecond)
          .setNanos(instant.getNano)
          .build()

    def timestampToInstant(timestamp: Timestamp): Instant =
        Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos)
}

class Splitter extends FlatMapFunction[String, Tuple2[String, Integer]] {
    override def flatMap(sentence: String, out: Collector[Tuple2[String, Integer]]): Unit =
        sentence.split("\\s+").foreach{ word: String => out.collect(new Tuple2(word, 1)) }
}


object Main {
    import TimeUtils._

    def main(args: Array[String]): Unit = {
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

        val env = StreamExecutionEnvironment.getExecutionEnvironment
        val dataStream = env
          .fromData(List("hello it's me, I've been waiting", "hey how are you").asJava)
          .flatMap(new Splitter())
          .keyBy((v: Tuple2[String, Integer]) => v.f0)
          .window(TumblingProcessingTimeWindows.of(Duration.ofSeconds(5)))
          .sum(1)

        dataStream.print()

        env.execute("hello worldcount")

        println("bye")
    }
}
