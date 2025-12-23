package io.github.demiourgoi.linoleum.examples

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.jdk.CollectionConverters._

import io.opentelemetry.proto.trace.v1.Span
import io.github.demiourgoi.linoleum.messages.{LinoleumSpanInfo, SpanInfo}
import io.github.demiourgoi.linoleum.maude.MaudeModules

@RunWith(classOf[JUnitRunner])
class MainTest extends org.specs2.mutable.Specification {
  "Considering a set of simple Linoleum examples" >> {
    "for the MaudeLotrImageGenSafety example" >> {
      "FIXME" >> {
        val traceMaudeModule = MaudeModules.traceTypesModule
        traceMaudeModule must not beNull

        // FIXME consider load file without getting modules 
        MaudeModules.loadStdModule("model-checker.maude", "SATISFACTION")  must not beNull
      
        val m = MaudeModules.loadModule("maude/lotrbot_imagegen_safety.maude", "IMAGEGEN-SAFEY")
        m must not beNull   

        val spanInfo = SpanInfo
          .newBuilder()
          .setSpan(Span.newBuilder().build())
          .build()

        ok
      }
    }
  }
}


// load ~/systems/maude/latest/model-checker.maude .
// load ~/git/demiourgoi/Linoleum/maude/linoleum/trace.maude .
// load lotrbot_imagegen_safety.maude .