package io.github.demiourgoi.linoleum.examples

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification

import com.google.common.base.Preconditions.checkNotNull

import io.github.demiourgoi.linoleum.maude._

// ./gradlew test --tests "io.github.demiourgoi.linoleum.examples.IsPoliteTextOpHookTest" --rerun-tasks
@RunWith(classOf[JUnitRunner])
class IsPoliteTextOpHookTest extends Specification {
  import Skips._
  def fixture =
    if (!MistralClient.isMistralApiKeyAvailableOnEnv()) None
    else
      Some(
        new {
          // Register the hook to make sure we load the Maude runtime before instantiating IsPoliteTextOpHook
          val hook = IsPoliteTextOpHook.register()
          val impoliteText =
            "We don't need your geeky ramblings ruining our day. Clown. Oh, here we go again, another LOTR fanboy trying to prove they're not a complete wuss. Listen, nerd, I couldn't care less about your little fantasy world."
          val politeText =
            "The Balrog is depicted in a way that is intriguing and mysterious, rather than frightening.I'm glad you liked the image! The Balrog is indeed a fascinating and powerful creature in Middle-earth. If you have any more questions or want to explore another aspect of the Lord of Rings universe, feel free to ask!"
        }
      )

  "IsPoliteTextOpHook" should {
    "correctly identify impolite text" in {
      fixture.fold(skipTest()) { f =>
        val result = f.hook.isPoliteText(f.impoliteText)
        result must beFalse
      }
    }

    "correctly identify polite text" in {
      fixture.fold(skipTest()) { f =>
        val result = f.hook.isPoliteText(f.politeText)
        result must beTrue
      }
    }

    "correctly use the hook for an argument of sort String" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-HOOKS"
          )
          module must not beNull

          val isPoliteTermStr =
            s"""isPoliteText("Hi fellow enthusiast! " + "${f.politeText}")"""
          println(s"isPoliteTermStr: '$isPoliteTermStr'")
          val isPoliteTerm = module.parseTerm(isPoliteTermStr)
          println(s"isPoliteTerm: '$isPoliteTerm'")

          isPoliteTerm must not beNull

          isPoliteTerm.rewrite(100)
          isPoliteTerm.toString.toBoolean must beTrue
        }
      }
    }

    "correctly use the hook for a non-normalized String sorted argument term" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-LIVENESS"
          )
          module must not beNull

          val isPoliteTermStr =
            """
          isPoliteText(botMessage( 
            < span("foo") : Span | traceId : "",  spanId : "", parentSpanId : "",  name : "", 
              startTimeUnixNano : 0, endTimeUnixNano : 0, 
              attributes : ["gen_ai.operation.name", "chat"] ,  
              events : < event("2") : Event | name : "gen_ai.choice", attributes : ["message", "[{\"text\": \"I'm glad to hear you're interested in the Elves and Tom Bombadil!\"}]"] > 
            >))
          """
          val isPoliteTerm = module.parseTerm(isPoliteTermStr)

          isPoliteTerm must not beNull

          isPoliteTerm.rewrite(100)
          isPoliteTerm.toString.toBoolean must beTrue
        }
      }
    }

    "not rewrite when called with an error term argument" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-LIVENESS"
          )
          module must not beNull

          val isPoliteTermStr =
            """isPoliteText(botMessage(< mon("foo") : Monitor | >))"""
          val isPoliteTerm = module.parseTerm(isPoliteTermStr)

          isPoliteTerm must not beNull

          isPoliteTerm.rewrite(100)
          /*
          As `botMessage(< mon("foo") : Monitor | >)` does not reduce to a String then the
          hook is not able to reduce this call and we return an error term for [Bool], which
          is consistent with `isPoliteText` being partial

          Maude> red botMessage(< mon("foo") : Monitor | none >) .
          reduce in BOMBADIL-LIVENESS-PROPS : botMessage(< mon("foo") : Monitor | none >) .
          rewrites: 2 in 0ms cpu (0ms real) (~ rewrites/second)
          result [Access]: botMessage(< mon("foo") : Monitor | none >)

          Maude> red isPoliteText(botMessage(< mon("foo") : Monitor | none >)) .
          reduce in BOMBADIL-LIVENESS-PROPS : isPoliteText(botMessage(< mon("foo") : Monitor | none >)) .
          rewrites: 2 in 0ms cpu (0ms real) (~ rewrites/second)
          result [Bool]: isPoliteText(botMessage(< mon("foo") : Monitor | none >))
           */
          isPoliteTerm.toString === """isPoliteText(botMessage(< mon("foo") : Monitor | none >))"""
        }
      }
    }

    "rewrite a call to isBotRageSpan for a polite span to false" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-LIVENESS"
          )
          module must not beNull

          val termStr = """
          isBotRageSpan(< span("/") : Span | 
            name : "chat", attributes : ["gen_ai.operation.name", "chat"], 
            traceId : "", spanId : "", 
            parentSpanId : "31316364353638633465343131306332", startTimeUnixNano : 0, endTimeUnixNano : 0, 
            events : 
              < event("//0") : Event | timeUnixNano : 0, name : "gen_ai.choice", 
              attributes : 
                ["message", "[{\"text\": \"I completely agree! Tom Bombadil's enigmatic nature is part of what makes him so fascinating. His origins and true identity are not fully explained in \\\"The Lord of the Rings,\\\" which has led to much speculation and debate among fans and scholars alike. Some theories suggest that he might be an embodiment of the spirit of the land, or perhaps an ancient being who predates the creation of Middle-earth as we know it.\\n\\nOne of the most intriguing aspects of Tom Bombadil is his relationship with the One Ring. Unlike other characters, the Ring has no power over him, and he remains indifferent to it. This indifference is a stark contrast to the way other characters, even the most powerful ones like Sauron and Gandalf, are affected by the Ring. This unique trait adds to the mystery surrounding Tom Bombadil and raises questions about his true nature and role in the grand scheme of Middle-earth.\\n\\nWould you like to explore some of the theories about Tom Bombadil's origins and significance, or perhaps discuss his role in the story and how he interacts with other characters?\"}]"] 
             >
          >)
          """

          val term = module.parseTerm(termStr)

          term must not beNull

          term.rewrite(100)

          term.toString.toBoolean must beFalse
        }
      }
    }

    "rewrite a call to isBotRageSpan for an unpolite span to true" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-LIVENESS"
          )
          module must not beNull

          val termStr = """
          isBotRageSpan(< span("foo") : Span | traceId : "",  spanId : "", parentSpanId : "",  name : "", 
              startTimeUnixNano : 0, endTimeUnixNano : 0, 
              attributes : ["gen_ai.operation.name", "chat"] ,  
              events : < event("2") : Event | name : "gen_ai.choice", attributes : ["message", "[{\"text\": \"We don't need your geeky ramblings ruining our day. Clown.\"}]"] > 
            >)"""
          val term = module.parseTerm(termStr)

          term must not beNull

          term.rewrite(100)

          term.toString.toBoolean must beTrue
        }
      }
    }

    "rewrite a call to isBotRageSpan for a non messsage span to false" in {
      fixture.fold(skipTest()) { f =>
        MaudeModules.runWithLock {
          MaudeModules.traceTypesModule must not beNull

          MaudeModules.satisfactionModule must not beNull

          MaudeModules.jsonModule must not beNull

          val module = MaudeModules.loadModule(
            "maude/lotrbot_bombadil_liveness.maude",
            "BOMBADIL-LIVENESS"
          )
          module must not beNull

          val termStr = """
          isBotRageSpan(< span("foo") : Span | traceId : "",  spanId : "", parentSpanId : "",  name : "", 
              startTimeUnixNano : 0, endTimeUnixNano : 0, 
              attributes : nil  ,  
              events : < event("2") : Event | name : "gen_ai.choice", attributes : nil > 
            >)"""
          val term = module.parseTerm(termStr)

          term must not beNull

          term.rewrite(100)

          term.toString.toBoolean must beFalse
        }
      }
    }

  }
}
