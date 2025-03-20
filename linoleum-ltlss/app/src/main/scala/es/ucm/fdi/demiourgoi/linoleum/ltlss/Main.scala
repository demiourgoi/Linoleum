package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._
import com.google.protobuf.Timestamp
import io.grpc.{Channel, ManagedChannelBuilder}
import io.grpc.stub.StreamObserver
import io.jaegertracing.api_v3.{QueryServiceGrpc, QueryServiceOuterClass}
import io.opentelemetry.proto.trace.v1.TracesData

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters._
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.java.tuple.Tuple2
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
        val asyncClient = QueryServiceGrpc.newStub(channel)

        // https://github.com/jaegertracing/jaeger-idl/blob/main/proto/api_v3/query_service.proto
        val now = Instant.now()
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

        asyncClient.findTraces(findTracesRequest, new StreamObserver[TracesData]{
            import messages._

            // Per https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/StreamObserver.html
            // this class must be thread-compatible, "This might mean surrounding every
            // method call with a synchronized block or creating a wrapper object where
            // every method is synchronized (like Collections.synchronizedList())"
            private var numSpans = 0

            override def onNext(traces: TracesData): Unit = {
                // https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
                traces.getResourceSpansList.forEach{ resourceSpans => {
                    val resource = resourceSpans.getResource
                    val scopeSpansList = resourceSpans.getScopeSpansList
                    println(s"Received ${scopeSpansList.size()} scope span lists for resource ${resource}")
                    scopeSpansList.forEach{scopeSpan =>
                        val spanList = scopeSpan.getSpansList
                        val scope = scopeSpan.getScope
                        println(s"Received ${spanList.size()} spans for instrumentation scope $scope")
                        spanList.forEach{span =>
                            val spanInfo = SpanInfo.newBuilder()
                              .setSpan(span).setScope(scope).setScopeSchemaUrl(scopeSpan.getSchemaUrl)
                              .setResource(resource).setResourceSchemaUrl(resourceSpans.getSchemaUrl)
                              .build()
                            println(s"Received span with trace id ${spanInfo.hexTraceId} and span id ${spanInfo.hexSpanId}: $spanInfo")
                            numSpans += 1
                        }
                    }
                }}

            }

            override def onError(t: Throwable): Unit =
                println(s"ERROR: Received terminating error from the stream: $t")

            override def onCompleted(): Unit =
                println(s"Received $numSpans spans with success")
        })

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
