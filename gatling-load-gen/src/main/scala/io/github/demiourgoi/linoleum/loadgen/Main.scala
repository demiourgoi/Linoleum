package io.github.demiourgoi.linoleum.loadgen

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

/** Entry point for the Kafka load generator.
  *
  * Launches the Gatling simulation programmatically. Kafka bootstrap servers
  * default to localhost:9092 but can be overridden via:
  *   -Dkafka.bootstrap.servers=host:port
  *
  * Prerequisites (from linoleum/DEVELOPER_GUIDE.md):
  *   1. cd linoleum && make release        (publish linoleum to ~/.m2)
  *   2. cd linoleum && make compose/start  (start Kafka, MongoDB, etc.)
  */
object Main {
  def main(args: Array[String]): Unit = {
    val bootstrapServers = sys.props.getOrElse(
      "kafka.bootstrap.servers",
      "localhost:9092"
    )

    println(s"[INFO] Kafka bootstrap servers: $bootstrapServers")
    println(s"[INFO] Topic: otlp_spans")
    println(s"[INFO] Java version: ${System.getProperty("java.version")}")

    val props = new GatlingPropertiesBuilder()
      .simulationClass(classOf[SpanTrafficSimulation].getName)
      // Gatling results directory; we don't use them for analysis but keep them
      .resultsDirectory("results")

    Gatling.fromMap(props.build)
  }
}
