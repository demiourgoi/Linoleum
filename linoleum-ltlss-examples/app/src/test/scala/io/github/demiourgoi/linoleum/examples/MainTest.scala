package io.github.demiourgoi.linoleum.examples

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.jdk.CollectionConverters._

import io.opentelemetry.proto.common.v1.{AnyValue, KeyValue}
import io.opentelemetry.proto.trace.v1.Span

import io.github.demiourgoi.linoleum.messages._
import io.github.demiourgoi.linoleum.maude.MaudeModules

@RunWith(classOf[JUnitRunner])
class MaudeLotrImageGenSafetyTest extends org.specs2.mutable.Specification {
    val rewriteBound = 100

    def spanBuilderToSpanInfo(span: Span.Builder): SpanInfo =
      SpanInfo
        .newBuilder()
        .setSpan(span.build())
        .build()   

    def nonImageSpan = spanBuilderToSpanInfo(Span.newBuilder())

    def imageSpan(durationNanos: Int): SpanInfo = {
      val attributes = List(
        KeyValue.newBuilder()
        .setKey("gen_ai.operation.name")
        .setValue(AnyValue.newBuilder().setStringValue("execute_tool").build())
        .build(),
        KeyValue.newBuilder()
        .setKey("gen_ai.tool.name")
        .setValue(AnyValue.newBuilder().setStringValue("generate_image").build())
        .build()
      )
    
      val span = Span.newBuilder()
        .addAllAttributes(attributes.asJava)
        .setStartTimeUnixNano(0)
        .setEndTimeUnixNano(durationNanos)

      spanBuilderToSpanInfo(span)
    }

    "For the MaudeLotrImageGenSafety example" >> {
      "we can track image generation time using the monitor" >> {
        val traceMaudeModule = MaudeModules.traceTypesModule
        traceMaudeModule must not beNull

        MaudeModules.loadStdLibProgram("model-checker.maude")

        val safetyMod = MaudeModules.loadModule("maude/lotrbot_imagegen_safety.maude", "IMAGEGEN-SAFETY")
        safetyMod must not beNull   

        // Initial configuration
        val monId = "safety"
        val monOid = s"""mon("$monId")"""
        var soup = safetyMod.parseTerm(s"""initConfig("$monId")""")
        soup must not beNull 
        // ----------------------

        // Non image span end does nothing
        SpanEnd(nonImageSpan).toMaude(monOid)
        soup = safetyMod.parseTerm(s"""${SpanEnd(nonImageSpan).toMaude(monOid)} ${soup.toString}""")
        soup must not beNull

        soup.rewrite(rewriteBound)
        soup.toString() === s"""< mon("$monId") : Monitor | timeSpentOnImageGenNanos : 0 >"""
        // ----------------------

        // image span end increases time count
        soup = safetyMod.parseTerm(s"""${SpanEnd(imageSpan(10)).toMaude(monOid)} ${soup.toString}""")
        soup must not beNull

        soup.rewrite(rewriteBound)
        soup.toString() === s"""< mon("$monId") : Monitor | timeSpentOnImageGenNanos : 10 >"""
        // ----------------------

        // image span start does not increase time count
        soup = safetyMod.parseTerm(s"""${SpanStart(imageSpan(10)).toMaude(monOid)} ${soup.toString}""")
        soup must not beNull

        soup.rewrite(rewriteBound)
        soup.toString() === s"""< mon("$monId") : Monitor | timeSpentOnImageGenNanos : 10 >"""
        // ----------------------


        // image span end increases time count again
        soup = safetyMod.parseTerm(s"""${SpanEnd(imageSpan(5)).toMaude(monOid)} ${soup.toString}""")
        soup must not beNull

        soup.rewrite(rewriteBound)
        soup.toString() === s"""< mon("$monId") : Monitor | timeSpentOnImageGenNanos : 15 >"""
        // ----------------------

        /* TODO
        - [ ] Linoleum events to maude ... TODO test on lonilem
        - [ ] Move msg spanStart and spanEnd to common module: as part of LinoleumEvent::toMaude
        - [ ] Expose maude property method to assert on each of the steps of the evaluation: actually 
        similar to what we do with formulas
        */
      }
    }
  
}
