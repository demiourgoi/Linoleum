package io.github.demiourgoi.linoleum.examples

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification

import com.google.common.base.Preconditions.checkNotNull

import io.github.demiourgoi.linoleum.maude._

@RunWith(classOf[JUnitRunner])
class IsPoliteTextOpHookTest extends Specification {
  def fixture =
    if (!MistralClient.isMistralApiKeyAvailableOnEnv()) None
    else
      Some(
        new {
          // Register the hook to make sure we load the Maude runtime before instantiating IsPoliteTextOpHook
          val hook = MaudeModules.connectEqHook("isPoliteTextOpHook", new IsPoliteTextOpHook())
        }
      )

  def skipTest() = {
    println("Skipping test")
    ok
  }

  "IsPoliteTextOpHook" should {
    "correctly identify impolite text" in {
      fixture.fold(skipTest()) { f =>
        // Test impolite text
        val impoliteText =
          "We don't need your geeky ramblings ruining our day. Clown. Oh, here we go again, another LOTR fanboy trying to prove they're not a complete wuss. Listen, nerd, I couldn't care less about your little fantasy world."
        val result = f.hook.isPoliteText(impoliteText)

        // Verify the result is false for impolite text
        result must beFalse
      }
    }

    "correctly identify polite text" in {
      fixture.fold(skipTest()) { f =>
        // Test polite text
        val politeText =
          "The Balrog is depicted in a way that is intriguing and mysterious, rather than frightening.I'm glad you liked the image! The Balrog is indeed a fascinating and powerful creature in Middle-earth. If you have any more questions or want to explore another aspect of the Lord of Rings universe, feel free to ask!"
        val result = f.hook.isPoliteText(politeText)

        // Verify the result is true for polite text
        result must beTrue
      }
    }
  }
}
