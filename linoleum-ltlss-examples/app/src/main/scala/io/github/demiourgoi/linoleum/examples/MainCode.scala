package io.github.demiourgoi.linoleum.examples

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

import io.github.demiourgoi.linoleum.Linoleum
import io.github.demiourgoi.linoleum.config._
import io.github.demiourgoi.linoleum.messages._

package object maudeLotrCommon {
  import io.github.demiourgoi.linoleum.maude._

  def shouldIgnoreWindowOlderThanOneDay(
      agentName: String,
      events: List[LinoleumEvent]
  ): Boolean = {
    // Using agent names with format f"lotrbot/{uuid.uuid4()}/{int(time.time())}"
    val epoch = agentName.split("/").last.toLong
    Instant
      .ofEpochSecond(epoch)
      .isBefore(Instant.now().minus(1, ChronoUnit.DAYS))
  }

  val stateConfig = Some(MaudeMonitor.StateConfig(
    ttl = Duration.ofDays(1),
    // note Linoleum refreshes TTL on state read
    shouldIgnoreWindow = shouldIgnoreWindowOlderThanOneDay
  ))
}

package object maudeLotrImageGenSafetyMonitor {
  import io.github.demiourgoi.linoleum.maude._
  import maudeLotrCommon._
  import Main.localCfg

  val log = LoggerFactory.getLogger(
    "io.github.demiourgoi.linoleum.examples.maudeLotrImageGenSafetyMonitor"
  )

  val monOid = s"""mon("safety")"""
  val monitor =
    MaudeMonitor(
      name = "lotrbot does not spend too much time generating images",
      program = "maude/lotrbot_imagegen_safety.maude",
      module = "IMAGEGEN-SAFETY-PROPS",
      monitorOid = monOid,
      initialSoup = s"""initConfig($monOid)""",
      property = "imageGenUsageWithinLimits",
      // Note this works because lotrbot makes sure to add "lotrbot.chat_id" to all
      // trace spans, not only the root spans, and that way Flink is able to group
      // all spans by the lotrbot chat id
      keyBy = KeyByStringSpanAttribute("lotrbot.chat_id"),
      stateConfig=stateConfig,
      config = MaudeMonitor.EvaluationConfig(
        messageRewriteBound = 100,
        sessionGap = Duration.ofSeconds(5)
      )
    )

  def run(): Unit = {
    log.warn("Running maudeLotrImageGenSafetyMonitor example")
    Linoleum.execute(
      localCfg.copy(jobName = "maudeLotrImageGenSafetyMonitor"),
      monitor
    )
    log.warn("Ending program")
  }
}

package object maudeLotrBombadilLivenessMonitor {
  import io.github.demiourgoi.linoleum.maude._
  import maudeLotrCommon._
  import Main.localCfg

  val log = LoggerFactory.getLogger(
    "io.github.demiourgoi.linoleum.examples.maudeLotrBombadilLivenessMonitor"
  )

  val monOid = s"""mon("liveness")"""
  val monitor =
    MaudeMonitor(
      name =
        "everytime the user mentions Tom Bombadil, lotrbot eventually gets angry",
      program = "maude/lotrbot_bombadil_liveness.maude",
      module = "BOMBADIL-LIVENESS-PROPS",
      monitorOid = monOid,
      initialSoup = s"""initConfig($monOid)""",
      property = "bombadilSafeSatisfied",
      // Note this works because lotrbot makes sure to add "lotrbot.chat_id" to all
      // trace spans, not only the root spans, and that way Flink is able to group
      // all spans by the lotrbot chat id
      keyBy = KeyByStringSpanAttribute("lotrbot.chat_id"),
      dependencyPrograms = List(
        "maude/json/date.maude",
        "maude/json/value.maude",
        "maude/json/json.maude"
      ),
      rlHooks = List((IsPoliteTextOpHook.hookOpName, IsPoliteTextOpHook.apply)),
      stateConfig=stateConfig,
      config = MaudeMonitor.EvaluationConfig(
        messageRewriteBound = 100,
        sessionGap = Duration.ofSeconds(5)
      )
    )

  def run(): Unit = {
    log.warn("Running maudeLotrBombadilLivenessMonitor example")
    Linoleum.execute(
      localCfg.copy(jobName = "maudeLotrBombadilLivenessMonitor"),
      monitor
    )
    log.warn("Ending program")
  }
}

object Main {
  private val log = LoggerFactory.getLogger(Main.getClass.getName)
  val localCfg = LinoleumConfig(
    jobName = "",
    localFlinkEnv = true,
    sink = SinkConfig().copy(logMaudeTerms = true)
  )

  object ExampleId extends Enumeration {
    type ExampleId = Value
    val MaudeLotrImageGenSafety, MaudeLotrBombadilLiveness = Value
  }

  private def parseExampleId(args: Array[String]): Option[ExampleId.Value] = {
    if (args.nonEmpty) {
      ExampleId.values
        .find(_.toString == args(0))
    } else {
      None
    }
  }

  def main(args: Array[String]): Unit = {
    val exampleId = parseExampleId(args)
    log.info("Running example program with id '{}'", Array[AnyRef](exampleId): _*)
    exampleId match {
      case None => log.error("Unknown example program name")
      case Some(ExampleId.MaudeLotrImageGenSafety) =>
        maudeLotrImageGenSafetyMonitor.run()
      case Some(ExampleId.MaudeLotrBombadilLiveness) =>
        maudeLotrBombadilLivenessMonitor.run()
      case _ => throw new NotImplementedError("WIP")
    }
  }
}
