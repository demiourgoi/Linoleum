package io.github.demiourgoi.linoleum.examples

import org.slf4j.LoggerFactory

import es.ucm.maude.bindings.{Hook, Term, HookData}

/**
 * Custom special operator hook for Maude that checks if text is polite.
 * This implements a Maude eq hook that can be used to define custom equality
 * behavior for checking text politeness.
 *
 * Based on the Maude bindings documentation:
 * https://fadoss.github.io/maude-bindings/#custom-special-operators
 * https://fadoss.github.io/maude-bindings/javadoc/es/ucm/maude/bindings/Hook.html
 * 
 * 
 * NOTE: The Maude runtime must be initialized before instantiating this class, otherwise
 * we get a java.lang.UnsatisfiedLinkError due to super class logic
 */
class IsPoliteTextOpHook extends Hook {
  import IsPoliteTextOpHook._
  private val mistral: MistralClient = new MistralClient()

  private val ASK_IS_POLITE_PROMPT = "evaluate the following text, and tell me if it is polite or not. You MUST ONLY answer \"yes\" or \"no\": %s"

  /**
   * Evaluates whether the given text is polite.
   * This method implements the actual logic for determining text politeness.
   *
   * @param text The text to evaluate for politeness
   * @return true if the text is considered polite, false otherwise
   */
  def isPoliteText(text: String): Boolean = {
    log.debug("Checking if text {} is polite", text)
    val prompt = String.format(ASK_IS_POLITE_PROMPT, text)
    val response = mistral.sendCompletion(prompt).getChoiceContentsString()
    log.debug("Classified text {} as polite {}", text, response)
    response.contains("yes")
  }

  /**
   * The main hook method that will be called by Maude when the custom operator is evaluated.
   * This implementation checks if the provided text is polite.
   *
   * @param argument The text argument to evaluate
   * @return The result of the politeness check as a Maude term
   */
  override def run(term: Term, data: HookData): Term = {
    var text: String = null // FIXME
    // as in https://fadoss.github.io/maude-bindings/#custom-special-operators
    val module = term.symbol().getModule()
    module.parseTerm(String.valueOf(isPoliteText(text)))
  }
}
object IsPoliteTextOpHook {
 val log = LoggerFactory.getLogger(classOf[IsPoliteTextOpHook])
}
