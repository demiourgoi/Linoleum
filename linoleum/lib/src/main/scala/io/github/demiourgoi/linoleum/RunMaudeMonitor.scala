package io.github.demiourgoi.linoleum

import scala.util.Try

import org.slf4j.LoggerFactory
import java.nio.file.Paths

import config.LinoleumConfig
import config.monitor.MaudeMonitorConfig

object RunMaudeMonitor {
  private val log = LoggerFactory.getLogger(RunMaudeMonitor.getClass.getName)

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println(
        "Usage: Main <Linoleum-config-file-path> <Maude-monitor-file-path>"
      )
      System.exit(1)
    }

    val linoleumConfigPath = Paths.get(args(0))
    val monitorConfigPath = Paths.get(args(1))
    log.info(
      "Loading configurations for linoleumConfigPath=[{}], monitorConfigPath=[{}]",
      Array[AnyRef](linoleumConfigPath, monitorConfigPath): _*
    )

    import scala.util.{Try, Success, Failure}

    Try {
      val linoleumConfig = LinoleumConfig.fromPath(linoleumConfigPath)
      log.info(
        "Linoleum configuration loaded with success from path {}: {}",
        Array[AnyRef](linoleumConfigPath, linoleumConfig): _*
      )
      val monitorConfig = MaudeMonitorConfig.fromPath(monitorConfigPath)
      log.info(
        "Maude monitor configuration loaded with success from path {}: {}",
        Array[AnyRef](monitorConfigPath, monitorConfig): _*
      )
      (linoleumConfig, monitorConfig)
    } match {
      case Failure(t) =>
        log.error(
          "Failure loading config from paths {} and {}",
          Array[AnyRef](linoleumConfigPath, monitorConfigPath, t): _*
        )
        System.exit(3)
      case Success((linoleumConfig, monitorConfig)) =>
        log.info("Running Linoleum job {}", linoleumConfig.jobName)

        Linoleum.execute(linoleumConfig, monitorConfig)
    }
  }
}
