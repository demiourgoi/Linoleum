package io.github.demiourgoi.linoleum

import org.slf4j.LoggerFactory
import java.nio.file.Paths

import config.LinoleumConfig

object RunMaudeMonitor {
  private val log = LoggerFactory.getLogger(RunMaudeMonitor.getClass.getName)

  def main(args: Array[String]): Unit = {
    // Validate command line arguments
    if (args.length == 0) {
      log.error("No configuration file path provided")
      println("Usage: Main <config-file-path>")
      System.exit(1)
    }
    
    if (args.length > 1) {
      log.error("Too many arguments provided")
      println("Usage: Main <config-file-path>")
      System.exit(2)
    }
    
    val configPath = Paths.get(args(0))
    
    try {
      val config = LinoleumConfig.fromPath(configPath)
      log.info("Linoleum configuration loaded with success from path {}", configPath)
      log.info("Running Linoleum job {}", config.jobName)
      // TODO: load maude monitor configuration from YAML
      // FIXME: Linoleum.execute(config)

    } catch {
      case e: Exception =>
        log.error("error loading config from path {}", configPath, e)
        System.exit(3)
    }
  }
}
