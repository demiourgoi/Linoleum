package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._
import io.grpc.{CallCredentials, Channel, Context, Grpc, Metadata, ManagedChannelBuilder, TlsChannelCredentials}
import com.grafana.tempopb.{SearchRequest, StreamingQuerierGrpc, QuerierGrpc, TraceByIDRequest}
// async because it is not StreamingQuerierBlockingStub
// import com.grafana.tempopb.StreamingQuerierGrpc.StreamingQuerierStub
import com.grafana.tempopb.StreamingQuerierGrpc.StreamingQuerierBlockingStub
import java.time.{Duration, Instant}
import java.util.concurrent.Executor
import java.nio.file.Path
import scala.io.Source
import com.google.protobuf.ByteString

object TempoAuthCredentials {
    // FIXME
    private val Token = {
        val tokenPath = Path.of(System.getProperty("user.home"), ".otel", "grafana.creds")
        val fileSrc = Source.fromFile(tokenPath.toString)
        val token = fileSrc.getLines().mkString
        fileSrc.close()
        // println(s"Using token [$token]") // FIXME
        token
    }
    val MetadataKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
    val UserKey = Metadata.Key.of("User", Metadata.ASCII_STRING_MARSHALLER)
    val ContextUsernameKey = Context.key("username")
}
class TempoAuthCredentials extends CallCredentials {
    import TempoAuthCredentials._
    override def applyRequestMetadata(requestInfo: CallCredentials.RequestInfo,
                                      appExecutor: Executor,
                                      metadataApplier: CallCredentials.MetadataApplier): Unit = {
        // FIXME try catch https://github.dev/mark-cs/grpc-call-credentials/tree/master/src/main/java/uk/co/markcs/grpc
        appExecutor.execute(() => {
            val headers = new Metadata()
            headers.put(MetadataKey, s"Basic $Token")
           // headers.put(UserKey, "780056") // FIXME
            metadataApplier(headers)
        })
    }
}

object Main extends App {
    val host =  "tempo-prod-10-prod-eu-west-2.grafana.net" // "tempo-prod-10-prod-eu-west-2.grafana.net/tempo"
    val credentials = new TempoAuthCredentials()
    val channel: Channel = {
        val builder = ManagedChannelBuilder.forAddress(host, 4317)
        // builder.usePlaintext()
        builder.useTransportSecurity()
        builder.build()
    }
    /*
    = Grpc.newChannelBuilderForAddress(
        host, 443,
        TlsChannelCredentials.create() // FIXME try with planin
    ).build() */
    val client = QuerierGrpc.newBlockingStub(channel)
        // StreamingQuerierGrpc.newBlockingStub(channel)
    val now = Instant.now()

    // returns an iterator because this is a server side streaming RPC
    val searchRequest = SearchRequest.newBuilder()
      // https://grafana.com/docs/tempo/latest/api_docs/#search unix epoch seconds
      .setStart(math.round(now.minus(Duration.ofMinutes(10)).toEpochMilli / 1000))
      .setEnd(math.round(now.toEpochMilli / 1000))
      .setLimit(10)
      .setSpansPerSpanSet(100)
      // https://grafana.com/docs/tempo/latest/traceql/
      .setQuery("{}")
      .build()
    println(s"Searching with query $searchRequest")
    val traceID = "1bf45bf4e4c16cb8f96cfd25ff0db648"
    val response = client.withCallCredentials(credentials).findTraceByID(
        TraceByIDRequest.newBuilder()
          .setTraceID(ByteString.copyFromUtf8(traceID))
          .build()
    )
    println("got response")
    println(response.getTrace)
   /* val responses = client
      .withCallCredentials(credentials)
      .searchRecent(searchRequest)
      // .search(searchRequest)
    println(responses)
    responses forEachRemaining { response =>
      println(response)
    }
    */

    val formula = always{ x: Int => x must be_>(0) } during 10
    Console.println(s"Hello ${formula}")
}
