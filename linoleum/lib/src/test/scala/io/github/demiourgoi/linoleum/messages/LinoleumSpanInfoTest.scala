package io.github.demiourgoi.linoleum.messages

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.ScalaCheck
import org.scalacheck.{Arbitrary, Gen}
import com.google.protobuf.ByteString
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.ArrayValue
import io.opentelemetry.proto.common.v1.KeyValueList
import scala.jdk.CollectionConverters._

import io.github.demiourgoi.sscheck.gen.UtilsGen
import io.github.demiourgoi.linoleum.maude.MaudeModules

@RunWith(classOf[JUnitRunner])
class LinoleumSpanInfoTest
    extends org.specs2.mutable.Specification
    with ScalaCheck {

  // Reduce for debugging
  private val maxNumItems = 2

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
      events <- UtilsGen.containerOfNtoM[List, Span.Event](
        0,
        maxNumItems,
        genSpanEvent
      )
    } yield {
      val spanBuilder = Span
        .newBuilder()
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

      SpanInfo
        .newBuilder()
        .setSpan(spanBuilder.build())
        .build()
    }
  }

  // Generator for primitive AnyValue types (non-recursive)
  private def genPrimitiveAnyValue: Gen[AnyValue] = Gen.oneOf(
    // String value
    Gen.alphaNumStr.suchThat(_.nonEmpty).map { str =>
      AnyValue.newBuilder().setStringValue(str).build()
    },
    // Boolean value
    Gen.oneOf(true, false).map { bool =>
      AnyValue.newBuilder().setBoolValue(bool).build()
    },
    // Integer value
    Gen.choose(Long.MinValue, Long.MaxValue).map { longVal =>
      AnyValue.newBuilder().setIntValue(longVal).build()
    },
    // Double value
    Gen.choose(Double.MinValue, Double.MaxValue).map { doubleVal =>
      AnyValue.newBuilder().setDoubleValue(doubleVal).build()
    },
    // Bytes value
    Gen.alphaNumStr.suchThat(_.nonEmpty).map { str =>
      AnyValue
        .newBuilder()
        .setBytesValue(com.google.protobuf.ByteString.copyFromUtf8(str))
        .build()
    }
  )

  // Generator for ArrayValue (recursive, uses sized generator)
  private def genArrayValue: Gen[AnyValue] = Gen.sized { size =>
    if (size <= 0) {
      // Base case: empty array
      Gen.const(
        AnyValue
          .newBuilder()
          .setArrayValue(ArrayValue.newBuilder().build())
          .build()
      )
    } else {
      // Generate 0 to size elements, letting ScalaCheck control the complexity
      for {
        numElements <- Gen.choose(0, size)
        elements <- UtilsGen.containerOfNtoM[List, AnyValue](
          0,
          numElements,
          Gen.resize(size / 2, genAnyValue)
        )
      } yield {
        val arrayBuilder = ArrayValue.newBuilder()
        elements.foreach(arrayBuilder.addValues)
        AnyValue.newBuilder().setArrayValue(arrayBuilder.build()).build()
      }
    }
  }

  // Generator for KeyValueList (recursive, uses sized generator)
  private def genKeyValueList: Gen[AnyValue] = Gen.sized { size =>
    if (size <= 0) {
      // Base case: empty key-value list
      Gen.const(
        AnyValue
          .newBuilder()
          .setKvlistValue(KeyValueList.newBuilder().build())
          .build()
      )
    } else {
      // Generate 0 to size key-value pairs, letting ScalaCheck control the complexity
      for {
        numPairs <- Gen.choose(0, size)
        keyValues <- UtilsGen.containerOfNtoM[List, KeyValue](
          0,
          numPairs,
          Gen.resize(size / 2, genKeyValue)
        )
      } yield {
        val kvListBuilder = KeyValueList.newBuilder()
        keyValues.foreach(kvListBuilder.addValues)
        AnyValue.newBuilder().setKvlistValue(kvListBuilder.build()).build()
      }
    }
  }

  // Main AnyValue generator using sized approach
  private def genAnyValue: Gen[AnyValue] = Gen.sized { size =>
    if (size <= 1) {
      // For very small sizes, generate only primitive types
      genPrimitiveAnyValue
    } else {
      // For larger sizes, include complex types with appropriate frequency
      Gen.frequency(
        // Primitive types (more frequent)
        (6, genPrimitiveAnyValue),
        // Array values (less frequent)
        (2, genArrayValue),
        // Key-value list values (less frequent)
        (2, genKeyValueList)
      )
    }
  }

  // Generator for KeyValue attributes (updated to use genAnyValue)
  private def genKeyValue: Gen[KeyValue] = for {
    key <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    value <- genAnyValue
  } yield {
    KeyValue
      .newBuilder()
      .setKey(key)
      .setValue(value)
      .build()
  }

  // Generator for Span events
  private def genSpanEvent: Gen[Span.Event] = for {
    name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
    time <- Gen.choose(0L, Long.MaxValue)
    attributes <- genAttributes
  } yield {
    Span.Event
      .newBuilder()
      .setName(name)
      .setTimeUnixNano(time)
      .addAllAttributes(attributes.asJava)
      .build()
  }

  private def genAttributes: Gen[List[KeyValue]] =
    UtilsGen.containerOfNtoM[List, KeyValue](0, maxNumItems, genKeyValue)

  "A LinoleumSpanInfo should" >> {
    "toMaudeSpanObject method" >> {
      "should always return a well formatted Maude term" >> prop {
        (spanInfo: SpanInfo) =>
          val linoleumSpanInfo = new LinoleumSpanInfo(spanInfo)
          val spanTermStr = linoleumSpanInfo.toMaude
          (spanTermStr must not beNull) and (spanTermStr must not be empty)

          val traceMaudeModule = MaudeModules.traceTypesModule
          traceMaudeModule must not beNull

          val spanTerm = traceMaudeModule.parseTerm(spanTermStr)

          spanTerm must not beNull

          spanTerm.reduce() === 0
      }.set(minTestsOk = 100)
    

    "should escape string values using Json String rules" >> {
      val jsonStringValue = """[{"text": "Hello!"}]"""
      val spanBuilder = Span
        .newBuilder()
        .setTraceId(ByteString.copyFromUtf8("testTraceId"))
        .setSpanId(ByteString.copyFromUtf8("testSpanId"))
        .setName("testSpan")
        .setStartTimeUnixNano(1000L)
        .setEndTimeUnixNano(2000L)
        .addAttributes(
          KeyValue
            .newBuilder()
            .setKey("content")
            .setValue(
              AnyValue.newBuilder().setStringValue(jsonStringValue).build()
            )
            .build()
        )

      val spanInfo = SpanInfo
        .newBuilder()
        .setSpan(spanBuilder.build())
        .build()

      val linoleumSpanInfo = new LinoleumSpanInfo(spanInfo)
      val spanTermStr = linoleumSpanInfo.toMaude
      
      // Verify the Maude term is not null and not empty
      (spanTermStr must not beNull) and (spanTermStr must not be empty)

      spanTermStr must contain("content")

      spanTermStr must contain("[{'text': 'Hello!'}]")

      // Verify the Maude term can be parsed and reduced
      val traceMaudeModule = MaudeModules.traceTypesModule
      traceMaudeModule must not beNull

      val spanTerm = traceMaudeModule.parseTerm(spanTermStr)
      (spanTerm must not beNull) and (spanTerm.reduce() === 0)
      }
    }
  }
}
