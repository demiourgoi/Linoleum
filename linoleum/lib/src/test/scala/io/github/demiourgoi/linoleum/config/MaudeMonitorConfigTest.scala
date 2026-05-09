package io.github.demiourgoi.linoleum.config

import java.nio.file.Paths

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MaudeMonitorConfigTest extends AnyFlatSpec with Matchers {

  "MaudeMonitorConfig.fromPath" should "parse a YAML file with trace-id keyBy" in {
    val maudeMonitor = io.github.demiourgoi.linoleum.config.monitor.MaudeMonitorConfig.fromPath(
      Paths.get("src/test/resources/maude-monitor-test.yaml")
    )

    maudeMonitor.name shouldBe "Test Maude Monitor"
    maudeMonitor.program shouldBe "maude/lotrbot_imagegen_safety.maude"
    maudeMonitor.module shouldBe "LOTRBOT-IMAGE-SAFETY"
    maudeMonitor.monitorOid shouldBe "monitor"
    maudeMonitor.initialSoup shouldBe "< monitor : Monitor | state : initial >"
    maudeMonitor.property shouldBe "safe"
    maudeMonitor.keyBy.toString should include("KeyByStringSpanAttribute")
    maudeMonitor.config.messageRewriteBound shouldBe 100
    maudeMonitor.config.sessionGap.getSeconds shouldBe 300
    maudeMonitor.config.allowedLateness.getSeconds shouldBe 0
  }

  it should "parse a YAML file with default trace-id keyBy" in {
    val maudeMonitor = io.github.demiourgoi.linoleum.config.monitor.MaudeMonitorConfig.fromPath(
      Paths.get("src/test/resources/maude-monitor-default.yaml")
    )

    maudeMonitor.name shouldBe "Test Maude Monitor Default"
    maudeMonitor.keyBy.toString should include("KeyByTraceId")
  }
}