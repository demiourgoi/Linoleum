package io.github.demiourgoi.linoleum

import org.slf4j.LoggerFactory
import java.nio.file.Paths

import config.LinoleumConfig
import config.monitor.MaudeMonitorConfig

object RunMaudeMonitor {
  private val log = LoggerFactory.getLogger(RunMaudeMonitor.getClass.getName)

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println("Usage: Main <Linoleum-config-file-path> <Maude-monitor-file-path>")
      System.exit(1)
    }
    
    val linoleumConfigPath = Paths.get(args(0))
    val monitorConfigPath = Paths.get(args(1))
    
    try {
      val linoleumConfig = LinoleumConfig.fromPath(linoleumConfigPath)
      // val monitorConfig = MaudeMonitorConfig.fromPath(monitorConfigPath)
      // log.info("Configurations loaded with success from paths {} and {}", linoleumConfigPath, monitorConfigPath)
      // log.info("Running Linoleum job {}", linoleumConfig.jobName)
      // Linoleum.execute(linoleumConfig, monitorConfig)

    } catch {
      case e: Exception =>
        log.error("Failure loading config from from paths {} and {}", linoleumConfigPath, monitorConfigPath, e)
        System.exit(3)
    }
  }
}
