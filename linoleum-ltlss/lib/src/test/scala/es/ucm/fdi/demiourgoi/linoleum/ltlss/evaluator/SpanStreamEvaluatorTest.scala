package es.ucm.fdi.demiourgoi.linoleum.ltlss.evaluator

import io.opentelemetry.proto.trace.v1.Span 
import com.google.protobuf.ByteString

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.ScalaCheck
import org.specs2.execute.Result
import org.specs2.matcher.ThrownExpectations
import io.github.demiourgoi.sscheck.prop.tl.Formula._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.config._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.formulas._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.messages._
import es.ucm.fdi.demiourgoi.linoleum.ltlss.SpanInfo
import es.ucm.fdi.demiourgoi.linoleum.ltlss.TimeUtils._

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try
import java.{lang => jlang}
import java.time.Duration
import java.util.function.Supplier

@RunWith(classOf[JUnitRunner])
class SpanStreamEvaluatorTest
    extends org.specs2.mutable.Specification {

  def fixture =
    new {
      val formula = LinoleumFormula("test"){
        always { x : Letter => x.length > 0 } during 2
      }
      val evaluatorParams = SpanStreamEvaluatorParams(
        LinoleumConfig(
          jobName = "test", localFlinkEnv = true,
          evaluation = EvaluationConfig(
            tickPeriod=Duration.ofMillis(10), sessionGap=Duration.ofSeconds(1)
          )    
        ),
        formula=formula
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

    "Build letters by tumbling windows of tickPeriod duration" >> {
      "When events span across multiple time windows Then they are grouped into correct letters" >> {
        val f = fixture
        // Using tickPeriod of 10ms from fixture
        val rootSpan = testSpanInfo("root", true, startEpochMs = 1, endEpochMs = 48)
        val spanA = testSpanInfo("a", startEpochMs = 1, endEpochMs = 11)
        val spanB = testSpanInfo("b", startEpochMs = 12, endEpochMs = 40)
        val events = ListBuffer[LinoleumEvent](SpanStart(spanA), SpanEnd(spanA), SpanStart(spanB), SpanEnd(spanB))

        val letters = f.evaluator.processWindow.buildLetters(rootSpan, events).toList

        // We expect 4 letters based on the timestamps and tickPeriod of 10ms:
        // Letter 1 (1-10ms): SpanStart(root), SpanStart(spanA)
        // Letter 2 (11-20ms): SpanEnd(spanA), SpanStart(spanB)
        // Letter 3 (21-30ms): (empty)
        // Letter 4 (31-40ms): SpanEnd(spanB)
        // Letter 5 (41-50ms): SpanEnd(root)
        
        // Verify number of letters
        letters must have size(5)
        
        // Extract time values for verification
        val letterTimes = letters.map(_._1.millis)
        letterTimes === Range(1, 51, 10).map(_.longValue()).toList

        val eventTimes = letters.flatMap(_._2.map{_.epochUnixNano})
        eventTimes === List(1, 1, 11, 12, 40, 48).map(_*million)

        // Verify letter contents
        // First letter should contain SpanStart(root) and SpanStart(spanA)
        letters(0)._2 must have size(2)
        letters(0)._2(0) must beAnInstanceOf[SpanStart]
        letters(0)._2(0).span.spanId === "root"
        letters(0)._2(1) must beAnInstanceOf[SpanStart]
        letters(0)._2(1).span.spanId === "a"
        
        // Second letter should contain SpanEnd(spanA) and SpanStart(spanB)
        letters(1)._2 must have size(2)
        letters(1)._2(0) must beAnInstanceOf[SpanEnd]
        letters(1)._2(0).span.spanId === "a"
        letters(1)._2(1) must beAnInstanceOf[SpanStart]
        letters(1)._2(1).span.spanId === "b"
        
        // Third letter should be empty (no events in 20-29ns range)
        letters(2)._2 must have size(0)
        
        // Fourth letter should contain SpanEnd(spanB)
        letters(3)._2 must have size(1)
        letters(3)._2(0) must beAnInstanceOf[SpanEnd]
        letters(3)._2(0).span.spanId === "b"
        
        // Fifth letter should contain SpanEnd(root)
        letters(4)._2 must have size(1)
        letters(4)._2(0) must beAnInstanceOf[SpanEnd]
        letters(4)._2(0).span.spanId === "root"
      }
    }
  }

  def testSpanInfo(spanId: String, isRoot: Boolean=false, 
    startEpochMs: Long=0L, endEpochMs: Long=0L
  ): SpanInfo = {
    val span = Span.newBuilder()
    span.setSpanId(ByteString.copyFromUtf8(spanId))
    if (!isRoot) {
      span.setParentSpanId(ByteString.copyFromUtf8("foo"))
    }
    span.setStartTimeUnixNano(msToNanos(startEpochMs))
    span.setEndTimeUnixNano(msToNanos(endEpochMs))
  
    SpanInfo.newBuilder()
      .setSpan(span.build())
      .build()
  }
}
