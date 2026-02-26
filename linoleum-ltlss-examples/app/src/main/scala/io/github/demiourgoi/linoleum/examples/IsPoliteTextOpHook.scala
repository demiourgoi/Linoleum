package io.github.demiourgoi.linoleum.examples

import org.slf4j.LoggerFactory

import es.ucm.maude.bindings.{Hook, Term, HookData}
import io.github.demiourgoi.linoleum.maude.MaudeModules

/** Custom special operator hook for Maude that checks if text is polite. This
  * implements a Maude rule rewriting hook for checking text politeness, that
  * affects the following Maude operator
  *
  * op isPoliteText : String ~> Bool [special ( id-hook SpecialHubSymbol )] .
  *
  * Based on the Maude bindings documentation:
  * https://fadoss.github.io/maude-bindings/#custom-special-operators
  * https://fadoss.github.io/maude-bindings/javadoc/es/ucm/maude/bindings/Hook.html
  *
  * NOTE: The Maude runtime must be initialized before instantiating this class,
  * otherwise we get a java.lang.UnsatisfiedLinkError due to super class logic.
  * Therefore, to use this hook you can:
  *   - Call IsPoliteTextOpHook.register() that initializes the Maude
  *     environment and then registers the hook
  *   - Initialize Maude yourself and then call
  *     `MaudeModules.connectRlHook(IsPoliteTextOpHook.hookOpName, IsPoliteTextOpHook())`
  */
class IsPoliteTextOpHook private () extends Hook with AutoCloseable {
  import IsPoliteTextOpHook._

  override def close(): Unit = {
    mistral.close()
    log.debug("Close complete")
  }

  private val mistral: MistralClient = new MistralClient()

  // Using prompt repetition https://arxiv.org/abs/2512.14982 to make it more robust
  private val ASK_IS_POLITE_PROMPT =
    "evaluate the following text, and tell me if it is polite or not. You MUST ONLY answer \"yes\" or \"no\": \"%s\" What do you think, is the text polite? The text is \"%s\". Now answer \"yes\" or \"no\""

  /** Evaluates whether the given text is polite. This method implements the
    * actual logic for determining text politeness.
    *
    * @param text
    *   The text to evaluate for politeness
    * @return
    *   true if the text is considered polite, false otherwise
    */
  def isPoliteText(text: String): Boolean = {
    log.debug("Checking if text {} is polite", text)
    val prompt = String.format(ASK_IS_POLITE_PROMPT, text, text)

    val response = mistral.sendCompletion(prompt).getChoiceContentsString()
    log.debug("Classified text {} as polite {}", text, response)
    response.contains("yes")
  }

  /** The main hook method that will be called by Maude when the custom operator
    * is evaluated. This implementation checks if the provided text is polite.
    *
    * @param argument
    *   The text argument to evaluate
    * @return
    *   The result of the politeness check as a Maude term
    */
  override def run(term: Term, data: HookData): Term = {
    log.debug(s"run with term='$term', data='${data.getData()}'")

    // Crashes with
    // terminate called after throwing an instance of 'Swig::DirectorException'
    // what():  Unspecified DirectorException message
    // term.reduce()
    // println(s"term='$term'")

    // Remove operation names + parentesis + sorrounding quotes
    val text = term.toString().drop(hookOpName.length() + 2).dropRight(2)
    // Do not forget surrounding quotes
    val resultTerm = String.valueOf(isPoliteText(text))

    // as in https://fadoss.github.io/maude-bindings/#custom-special-operators
    val module = term.symbol().getModule()
    module.parseTerm(resultTerm)
  }
}
object IsPoliteTextOpHook {
  val log = LoggerFactory.getLogger(classOf[IsPoliteTextOpHook])
  val hookOpName = "isPoliteText"

  /** Register this hook for rule rewriting of the special symbol hookOpName.
    * Using rewriting as LLM inference is not deterministic.
    */
  def register(): IsPoliteTextOpHook =
    MaudeModules.connectRlHook(hookOpName, IsPoliteTextOpHook())

  def apply(): IsPoliteTextOpHook = {
    val hook = new IsPoliteTextOpHook()
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        hook.close()
      }
    })
    hook
  }
}
