package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import org.specs2.matcher.MustMatchers._

import io.grpc.{Channel, Grpc}
import io.grpc.InsecureChannelCredentials
import com.grafana.tempopb.StreamingQuerierGrpc
import com.grafana.tempopb.StreamingQuerierGrpc.StreamingQuerierStub

object Main extends App {
    val channel: Channel = Grpc.newChannelBuilderForAddress(
        "host", 8080, InsecureChannelCredentials.create() // FIXME
    ).build()
    val client: StreamingQuerierStub = StreamingQuerierGrpc.newStub(channel)

    val formula = always{ x: Int => x must be_>(0) } during 10
    Console.println(s"Hello ${formula}")
}
