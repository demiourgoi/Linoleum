package es.ucm.fdi.demiourgoi.linoleum.ltlss.evaluator

import io.opentelemetry.proto.trace.v1.Span 
import com.google.protobuf.ByteString

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.ScalaCheck
import org.specs2.execute.Result
import org.specs2.matcher.ThrownExpectations
import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.formulas._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.messages._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.SpanInfo

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try
import java.{lang => jlang}
import java.time.Duration


@RunWith(classOf[JUnitRunner])
class SpanStreamEvaluatorTest
    extends org.specs2.mutable.Specification {

  def fixture =
    new {
      val formula = always { x : Letter => x.length > 0 } during 2
      val linoleumFormula = LinoleumFormula("test", () => formula)
      val evaluatorParams = SpanStreamEvaluatorParams(
        formula=linoleumFormula,
        tickPeriod=Duration.ofMillis(100),
        sessionGap=Duration.ofSeconds(1)
      )
      val evaluator = new SpanStreamEvaluator(evaluatorParams)
    }

  
  "A SpanStreamEvaluator should" >> {
    "Collect events correctly" >> {
      "Given a single root and no duplicates Then we get an even list with Start and End events" >> {
        val f = fixture
        val spanInfos = List(testSpanInfo("root", true), testSpanInfo("a"), testSpanInfo("b")).asJava
        val (rootSpanOpt, events, failures) = f.evaluator.processWindow.collectLinoleumEvents(spanInfos)
        rootSpanOpt should beSome{spanInfo: SpanInfo => spanInfo.spanId === "root"}
        events.size % 2 === 0
        events.map{_.span.spanId}.toSet === Set("a", "b")
        val numStartEvents = events.collect{case SpanStart(_) => true}.size
        val numEndEvents = events.collect{case SpanEnd(_) => true}.size
        numStartEvents + numEndEvents === events.size
        failures must beEmpty
      }

      "When no spans are passed Then no events are collected" >> {
        val f = fixture
        val (rootSpanOpt, events, failures) = f.evaluator.processWindow.collectLinoleumEvents(List.empty[SpanInfo].asJava)
        rootSpanOpt should beNone
        events must beEmpty
        failures must beEmpty
      }

      "When a spans occurs twice Then it is ignored after the first time" >> {
        val f = fixture
        val spanInfos = List(testSpanInfo("root", true), testSpanInfo("a"), testSpanInfo("a")).asJava
        val (rootSpanOpt, events, failures) = f.evaluator.processWindow.collectLinoleumEvents(spanInfos)
        rootSpanOpt should beSome{spanInfo: SpanInfo => spanInfo.spanId === "root"}
        events must have size(2)
        events.map{_.span.spanId} == List("a", "a")
        failures must beEmpty
      }


      "When multiple roots occours Then we use the first one and log an error" >> {
        val f = fixture
        val spanInfos = List(testSpanInfo("root", true), testSpanInfo("root2", true), testSpanInfo("a")).asJava
        val (rootSpanOpt, events, failures) = f.evaluator.processWindow.collectLinoleumEvents(spanInfos)
        rootSpanOpt should beSome{spanInfo: SpanInfo => spanInfo.spanId === "root"}
        failures.size === 1
        failures(0).exception must beAnInstanceOf[SpanStreamEvaluator.EventCollectionMultipleRootSpansError]
      }
    }
  }

  def testSpanInfo(spanId: String, isRoot: Boolean=false): SpanInfo = {
    val span = Span.newBuilder()
    span.setSpanId(ByteString.copyFromUtf8(spanId))
    if (!isRoot) {
      span.setParentSpanId(ByteString.copyFromUtf8("foo"))
    }
  
    SpanInfo.newBuilder()
      .setSpan(span.build())
      .build()
  }
}