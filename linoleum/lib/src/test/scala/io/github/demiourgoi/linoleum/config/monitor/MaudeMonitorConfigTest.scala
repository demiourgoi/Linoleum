package io.github.demiourgoi.linoleum.config.monitor

import java.nio.file.Paths

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification

import es.ucm.maude.bindings.Hook
import io.github.demiourgoi.linoleum.messages.LinoleumEvent
import io.github.demiourgoi.linoleum.maude.MaudeModules

object MaudeMonitorConfigTest {
  class ExampleHook extends Hook {}
  def getHook(): Hook = new ExampleHook()
  def shouldIgnoreWindow(windowKey: String, orderedEvents: List[LinoleumEvent]): Boolean = true
}

@RunWith(classOf[JUnitRunner])
class MaudeMonitorConfigTest extends Specification {
  import MaudeMonitorConfigTest._

  "MaudeMonitorConfig.fromPath" should {

    "parse a YAML file with by-string-attribute keyBy and eqHooks" in {
      val resourceUrl = getClass.getResource("/maude-monitor-test.yaml")
      resourceUrl must not beNull

      val maudeMonitor = MaudeMonitorConfig.fromPath(Paths.get(resourceUrl.toURI))

      maudeMonitor.name must_== "Test Maude Monitor"
      maudeMonitor.program must_== "maude/lotrbot_imagegen_safety.maude"
      maudeMonitor.module must_== "LOTRBOT-IMAGE-SAFETY"
      maudeMonitor.monitorOid must_== "monitor"
      maudeMonitor.initialSoup must_== "< monitor : Monitor | state : initial >"
      maudeMonitor.property must_== "safe"
      maudeMonitor.keyBy.toString must contain("KeyByStringSpanAttribute")
      maudeMonitor.config.messageRewriteBound must_== 100
      maudeMonitor.config.sessionGap.getSeconds must_== 300
      maudeMonitor.config.allowedLateness.getSeconds must_== 0

      // hooks should be properly resolved
      maudeMonitor.eqHooks must haveSize(1)
      maudeMonitor.eqHooks.head._1 must_== "testOp"
      MaudeModules.connectEqHook(maudeMonitor.eqHooks.head._1, maudeMonitor.eqHooks.head._2()) must beAnInstanceOf[ExampleHook]

      // stateConfig should be present with resolved shouldIgnoreWindow
      maudeMonitor.stateConfig must beSome
      maudeMonitor.stateConfig.get.ttl.getSeconds must_== 3600
      maudeMonitor.stateConfig.get.shouldIgnoreWindow("", List.empty) must beTrue
    }

    "parse a YAML file with default trace-id keyBy and no stateConfig" in {
      val resourceUrl = getClass.getResource("/maude-monitor-default.yaml")
      resourceUrl must not beNull

      val maudeMonitor = MaudeMonitorConfig.fromPath(Paths.get(resourceUrl.toURI))

      maudeMonitor.name must_== "Test Maude Monitor Default"
      maudeMonitor.keyBy.toString must contain("KeyByTraceId")
      maudeMonitor.stateConfig must beNone
    }
  }
}