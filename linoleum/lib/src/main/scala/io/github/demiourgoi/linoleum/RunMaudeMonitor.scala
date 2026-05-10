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
      linoleumConfigPath,
      monitorConfigPath
    )

    Try {
      val linoleumConfig = LinoleumConfig.fromPath(linoleumConfigPath)
      log.info(
        "Linoleum configuration loaded with success from path {}: {}",
        linoleumConfigPath,
        linoleumConfig
      )
      val monitorConfig = MaudeMonitorConfig.fromPath(monitorConfigPath)
      log.info(
        "Maude monitor configuration loaded with success from path {}: {}",
        monitorConfigPath,
        monitorConfig
      )
      (linoleumConfig, monitorConfig)
    }.fold(
      { t =>
        log.error(
          "Failure loading config from paths {} and {}",
          linoleumConfigPath,
          monitorConfigPath,
          t
        )
        System.exit(3)
      },
      { case (linoleumConfig, monitorConfig) =>
        log.info("Running Linoleum job {}", linoleumConfig.jobName)

        Linoleum.execute(linoleumConfig, monitorConfig)
      }
    )
  }
}
