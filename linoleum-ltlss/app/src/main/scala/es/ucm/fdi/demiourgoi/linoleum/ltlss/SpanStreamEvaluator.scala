package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.{Formula, NextFormula, Time => SscheckTime}
import org.scalacheck.Prop
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows

import org.slf4j.LoggerFactory

import scala.collection.convert.ImplicitConversions.`iterator asScala`
import scala.math.pow
import source.SpanInfoStream

import com.google.protobuf.ByteString
import java.time.Duration
import java.util.PriorityQueue
import java.{lang => jlang}

package object formulas {
  sealed trait LinoleumEvent {
    /**
     * Returns UNIX Epoch time in nanoseconds positioning this event on the window
     * for the spans of a trace
     * https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
     * */
    def epochUnixNano: Long

    def span: SpanInfo
  }

  case class SpanStart(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getStartTimeUnixNano
  }

  case class SpanEnd(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getEndTimeUnixNano
  }

  /** Positions start events based on the span start, and end events
   * based on the span end */
  implicit val linoleumEventOrdering: Ordering[LinoleumEvent] =
    Ordering[Long].on(_.epochUnixNano)

  /** Formulas use a list of LinoleumEvent ordered by linoleumEventOrdering */
  type Letter = List[LinoleumEvent]

  type TimedLetter = (SscheckTime, Letter)

  /**
   * @param name    human-readable description for the formula
   * @param formula sscheck formula to evaluate. The formula must not perform any
   *                side effect when evaluated.
   * */
  case class LinoleumFormula(name: String, formula: Formula[Letter])
}

object FormulaValue {
  /** Builds a FormulaValue for the current evaluation state of Formula */
  def apply[T](formula: NextFormula[T]): FormulaValue =
    formula.result match {
      case Some(Prop.True) => True
      case Some(Prop.False) => False
      case _ => Undecided
    }
}

sealed trait FormulaValue

case object True extends FormulaValue with Serializable

case object False extends FormulaValue with Serializable

case object Undecided extends FormulaValue with Serializable

/** The result of evaluating a trace
 *
 * @param formulaName Name of the formula that was evaluated on the trace
 * */
case class EvaluatedTrace(traceId: ByteString, formulaName: String, formulaValue: FormulaValue) {
  val hexTraceId: String = messages.byteString2HexString(traceId)
}

package object evaluator {
  type VerifiedTraceStream = DataStream[EvaluatedTrace]
}
package evaluator {

  import com.google.protobuf.Timestamp
  import es.ucm.fdi.demiourgoi.sscheck.prop.tl.Formula.defaultFormulaParallelism
  import org.apache.flink.streaming.api.windowing.windows.TimeWindow
  import org.apache.flink.util.Collector

  import java.time.Instant
  import java.util
  import scala.collection.mutable.ListBuffer

  /**
   * @param tickPeriod      - The size of the tumbling windows we use to split the sequence of span events.
   * @param sessionGap      - When the formula doesn't have a defined safe world length, this is how much we wait for a new
   *                        span event to arrive before we close the set of spans for a trace.
   * @param allowedLateness - How much we wait for late spans to appear before we close the set of spans.
   *                        If greater than 0 then we might evaluate a formula on a trace more than once: on session
   *                        gap completion (watermark), and also on late elements arriving. Those additional evaluations
   *                        could change the evaluation of a trace. See https://github.com/juanrh/Linoleum/issues/3 for
   *                        more details.
   * */
  case class SpanStreamEvaluatorParams(
                                        formula: formulas.LinoleumFormula,
                                        tickPeriod: Duration,
                                        sessionGap: Duration,
                                        allowedLateness: Duration = Duration.ofMillis(0))

  /**
   * For each span in a trace we emit a SpanStart and SpanEnd event, and order all the events using the
   * span start time for SpanStart events and the end time for SpanEnd events.
   * We then discretize the ordered sequence using tumbling windows of `tickPeriod` duration.
   * The first letter always starts with `SpanStart(rootSpan)` and events before that are discarded as errors.
   *
   * Note a limitation is that if the clocks in the different hosts that emit the spans are too skewed then we
   * could lose the happens-before relation between parent and child spans when we order by event timestamp, that is
   * inherited from the span timestamps. This is a known limitation to be tackled on https://github.com/juanrh/Linoleum/issues/4
   *
   * If a span gets into the event more than once (two SpanInfo have the same spanId) then we only process one of them.
   * One of the replicas is chosen arbitrarily, so this assumes the trace instrumentation libraries only use the same
   * span id for identical spans.
   * */

  @SerialVersionUID(1L)
  class SpanStreamEvaluator(
                             @transient private val params: SpanStreamEvaluatorParams
                           )
    extends Function[SpanInfoStream, VerifiedTraceStream] with Serializable {

    import SpanStreamEvaluator._

    override def apply(spanStream: SpanInfoStream): VerifiedTraceStream = {
      val traceStream = spanStream.keyBy { span: SpanInfo => span.getSpan.getTraceId }

      traceStream
        .window(EventTimeSessionWindows.withGap(params.sessionGap))
        .allowedLateness(params.allowedLateness)
        .process(new ProcessWindow[TimeWindow](params.formula, params.tickPeriod))
    }
  }

  private object SpanStreamEvaluator {
    private val log = LoggerFactory.getLogger(SpanStreamEvaluator.getClass.getName)

    private class ProcessWindow[W <: TimeWindow](
                                                  @transient private val formula: formulas.LinoleumFormula,
                                                  @transient private val tickPeriod: Duration)
      extends ProcessWindowFunction[SpanInfo, EvaluatedTrace, ByteString, W] {

      import formulas._
      import messages._
      import TimeUtils._
      import scala.util.control.Breaks._

      override def process(
                            key: ByteString,
                            context: ProcessWindowFunction[SpanInfo, EvaluatedTrace, ByteString, W]#Context,
                            spanInfos: jlang.Iterable[SpanInfo],
                            out: Collector[EvaluatedTrace]): Unit = {
        // Build letters starting from the root span
        var rootSpanOpt: Option[SpanInfo] = None
        val eventsHeap = new PriorityQueue[LinoleumEvent](linoleumEventOrdering)
        val seenSpans = new util.HashSet[ByteString]()
        val eventFactories = List(SpanStart, SpanEnd)
        spanInfos.forEach { spanInfo =>
          val spanId = spanInfo.getSpan.getSpanId
          if (seenSpans.contains(spanId)) {
            log.debug("Skipping duplicate occurrence of span {}", spanInfo)
          } else {
            seenSpans.add(spanId)
            (spanInfo.isRoot, rootSpanOpt) match {
              case (true, None) =>
                rootSpanOpt = Some(spanInfo)

              case (true, Some(rootSpanInfo)) =>
                // TODO use side output to handle errors with simple error event with level, message and span, defined in proto
                log.error("Multiple root spans found for trace with id {}: {}, {}",
                  rootSpanInfo.hexTraceId, rootSpanInfo, spanInfo
                )

              case _ =>
                eventFactories.foreach { createEvent: (SpanInfo => LinoleumEvent) =>
                  eventsHeap.add(createEvent(spanInfo))
                }
            }
          }
        }

        rootSpanOpt.fold({
          // If the root span is missing then this is a window for a late span not added to the first session,
          // so we just discard this window
          // Per https://opentelemetry.io/docs/concepts/signals/traces/ there is always a single root span on every trace
          val someEvents = eventsHeap.iterator().take(5).toList
          if (someEvents.nonEmpty) {
            log.warn("Found late window for trace with id {}, skipping events {}, ... ",
              someEvents.head.span.hexTraceId, someEvents.mkString(", "))
          }
        }) { rootSpan =>
          log.info(s"Evaluating trace with id {}", rootSpan.hexTraceId)
          val evaluatedTrace = EvaluatedTrace(rootSpan.getSpan.getTraceId, formula.name,
            evaluateFormula(rootSpan.hexTraceId, buildLetters(rootSpan, eventsHeap))
          )
          log.info(s"Evaluated trace with id {} to {}", rootSpan.hexTraceId, evaluatedTrace)
          out.collect(evaluatedTrace)
        }
      }

      /**
       * Organizes the events in the trace as a set of discrete letters, that split the sequence of events in
       * tumbling windows of tickPeriod duration. The first letter always starts with `SpanStart(rootSpan)` and
       * events before that are discarded as errors.
       *
       * Precondition: events for rootSpan are NOT added to eventsHeap
       * */
      private[evaluator] def buildLetters(rootSpan: SpanInfo, eventsHeap: PriorityQueue[LinoleumEvent]): Iterator[TimedLetter] = {
        eventsHeap.add(SpanEnd(rootSpan))
        val startEvent = SpanStart(rootSpan)

        // traverse starting with startEvent and discarding later events, emitting errors accordingly
        new Iterator[TimedLetter]() {
          private val eventsIt = eventsHeap.iterator()
          // Invariant: if this iterator has next then current letter is not empty
          private var currentLetter: ListBuffer[LinoleumEvent] = ListBuffer(startEvent)

          private def letterToTimedLetter(letter: Letter): TimedLetter = {
            val letterTime = SscheckTime(nanosToMs(letter.head.epochUnixNano))
            (letterTime, letter)
          }

          private def flushCurrentLetter(eventOpt: Option[LinoleumEvent]): TimedLetter = {
            val nextLetter = currentLetter.toList
            currentLetter = eventOpt.fold[ListBuffer[LinoleumEvent]](ListBuffer.empty) {
              ListBuffer(_)
            }
            letterToTimedLetter(nextLetter)
          }

          override def hasNext: Boolean = eventsIt.hasNext || currentLetter.nonEmpty

          override def next(): TimedLetter = {
            for (event <- eventsIt) {
              val eventOffset = event.epochUnixNano - currentLetter.head.epochUnixNano
              val (eventIsLate, isLetterComplete) = (eventOffset < 0, eventOffset >= tickPeriod.toNanos)
              (eventIsLate, isLetterComplete) match {
                case (true, _) =>
                  // TODO side output for warning and recoverable errors; stream main output for formula evaluation errors
                  log.error("Dropping event {} happening before root letter start {}", event, currentLetter.head)

                case (false, true) =>
                  return flushCurrentLetter(Some(event))

                case (false, false) =>
                  currentLetter.addOne(event)
              }
            }

            if (currentLetter.nonEmpty) {
              // cleanup last letter
              return flushCurrentLetter(None)
            }

            throw new NoSuchElementException()
          }
        }
      }

      private[evaluator] def evaluateFormula(traceId: String, letters: Iterator[TimedLetter]): FormulaValue = {
        val currentFormula = formula.formula.nextFormula
        breakable {
          letters.foreach { timedLetter =>
            val (letterTime, letter) = timedLetter
            currentFormula.consume(letterTime)(letter)
            log.debug("Current formula for trace id {} at time {} is {}",
              traceId, letterTime, currentFormula)
            if (currentFormula.result.isDefined) break()
          }
        }
        FormulaValue(currentFormula)
      }
    }
  }

  object TimeUtils {
    private val million = pow(10, 6)

    def nanosToMs(nanos: Long): Long = (nanos / million).longValue

    def instantToTimestamp(instant: Instant): Timestamp =
      Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond)
        .setNanos(instant.getNano)
        .build()

    def timestampToInstant(timestamp: Timestamp): Instant =
      Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos)
  }
}
