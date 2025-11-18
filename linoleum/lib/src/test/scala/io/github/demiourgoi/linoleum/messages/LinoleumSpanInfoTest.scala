package io.github.demiourgoi.linoleum.messages

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.ScalaCheck
import org.scalacheck.{Arbitrary, Gen}
import com.google.protobuf.ByteString
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.common.v1.AnyValue
import scala.jdk.CollectionConverters._

import io.github.demiourgoi.sscheck.gen.UtilsGen

import es.ucm.maude.bindings.{maude, MaudeRuntime}
import es.ucm.maude.bindings.{Module => MaudeModule}


@RunWith(classOf[JUnitRunner])
class LinoleumSpanInfoTest extends org.specs2.mutable.Specification with ScalaCheck {

  // Reduce for debugging
  private val maxNumItems = 50

  // Generator for creating diverse SpanInfo objects
  implicit val arbitrarySpanInfo: Arbitrary[SpanInfo] = Arbitrary {
    for {
      traceId <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      spanId <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      parentSpanId <- Gen.option(Gen.alphaNumStr.suchThat(_.nonEmpty))
      name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      startTime <- Gen.choose(0L, Long.MaxValue)
      endTime <- Gen.choose(startTime, Long.MaxValue)
      attributes <- genAttributes
      events <- UtilsGen.containerOfNtoM[List, Span.Event](0, maxNumItems, genSpanEvent)
    } yield {
      val spanBuilder = Span.newBuilder()
        .setTraceId(ByteString.copyFromUtf8(traceId))
        .setSpanId(ByteString.copyFromUtf8(spanId))
        .setName(name)
        .setStartTimeUnixNano(startTime)
        .setEndTimeUnixNano(endTime)
        .addAllAttributes(attributes.asJava)
        .addAllEvents(events.asJava)
      
      parentSpanId.foreach { pid =>
        spanBuilder.setParentSpanId(ByteString.copyFromUtf8(pid))
      }
      
      SpanInfo.newBuilder()
        .setSpan(spanBuilder.build())
        .build()
    }
  }

  // Generator for KeyValue attributes
  private def genKeyValue: Gen[KeyValue] = for {
    key <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    value <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  } yield {
    KeyValue.newBuilder()
      .setKey(key)
      .setValue(AnyValue.newBuilder().setStringValue(value).build())
      .build()
  }

  // Generator for Span events
  private def genSpanEvent: Gen[Span.Event] = for {
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    time <- Gen.choose(0L, Long.MaxValue)
    attributes <- genAttributes
  } yield {
    Span.Event.newBuilder()
      .setName(name)
      .setTimeUnixNano(time)
      .addAllAttributes(attributes.asJava)
      .build()
  }

  private def genAttributes: Gen[List[KeyValue]] = 
      UtilsGen.containerOfNtoM[List, KeyValue](0, maxNumItems, genKeyValue)

  private lazy val traceMaudeModule: MaudeModule = {
    MaudeRuntime.init()
    // FIXME to prod constant
    MaudeRuntime.loadFromResources("maude/linoleum/trace.maude")
    maude.getModule("CLASS-OBJECTS")
  }

  "A LinoleumSpanInfo should" >> {
    "toMaudeSpanObject method" >> {
      "should always return a well formatted Maude term" >> prop { (spanInfo: SpanInfo) =>
        val linoleumSpanInfo = new LinoleumSpanInfo(spanInfo)
        val spanTermStr = linoleumSpanInfo.toMaudeSpanObject
        (spanTermStr must not beNull) and (spanTermStr must not be empty)

        traceMaudeModule must not beNull

        val spanTerm = traceMaudeModule.parseTerm(spanTermStr)

        spanTerm must not beNull

        spanTerm.reduce() === 0
      }.set(minTestsOk=100)
    }
  }
}
