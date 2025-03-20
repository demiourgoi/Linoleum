package es.ucm.fdi.demiourgoi.linoleum.ltlss

import es.ucm.fdi.demiourgoi.sscheck.prop.tl.{Formula, NextFormula}
import org.scalacheck.Prop
import org.apache.flink.streaming.api.datastream.DataStream

import sources.SpanInfoStream

import com.google.protobuf.ByteString
import java.time.Duration

package object formulas {
  sealed trait LinoleumEvent {
    /**
     * Returns UNIX Epoch time in nanoseconds positioning this event on the window
     * for the spans of a trace
     * https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto
     * */
    def epochUnixNano: Long
  }
  case class SpanStart(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getStartTimeUnixNano
  }
  case class SpanEnd(span: SpanInfo) extends LinoleumEvent {
    override def epochUnixNano: Long = span.getSpan.getEndTimeUnixNano
  }

  /** Positions start events based on the span start, and end events
   * based on the span end*/
  implicit val linoleumEventOrdering: Ordering[LinoleumEvent] =
    Ordering[Long].on(_.epochUnixNano)

  /** Formulas use a list of LinoleumEvent ordered by linoleumEventOrdering */
  type Letter = List[LinoleumEvent]

  /**
   * @param name human-readable description for the formula
   * */
  case class LinoleumFormula(formula: Formula[Letter], name: String)
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

/** The result of verifying a trace
 * @param formulaName Name of the formula that was evaluated on the trace
 * */
case class VerifiedTrace(traceId: ByteString, formulaName: String, formulaValue: FormulaValue) {
  val hexTraceId: String = messages.byteString2HexString(traceId)
}

package object evaluator {
  type VerifiedTraceStream = DataStream[VerifiedTrace]
}
package evaluator {
  /**
   * @param tickPeriod - letters will contain consecutive FIXME
   * */
  class SpanStreamEvaluator(
    @transient private val formula: formulas.LinoleumFormula,
    @transient private val tickPeriod: Duration,
  )
  extends Function[SpanInfoStream, VerifiedTraceStream]{

    override def apply(spanStream: SpanInfoStream): VerifiedTraceStream = {
      val traceStream = spanStream.keyBy{span: SpanInfo => span.getSpan.getTraceId}

      ??? // FIXME
    }
  }

}