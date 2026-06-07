package io.github.demiourgoi.linoleum.loadgen

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}

import java.util.Properties
import scala.jdk.CollectionConverters._

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
  private val TOPIC_NAME = "otlp_spans"
  private val NUM_PARTITIONS = 32
  private val REPLICATION_FACTOR = 1.toShort

  def main(args: Array[String]): Unit = {
    val bootstrapServers = sys.props.getOrElse(
      "kafka.bootstrap.servers",
      "localhost:9092"
    )

    println(s"[INFO] Kafka bootstrap servers: $bootstrapServers")
    println(s"[INFO] Topic: $TOPIC_NAME")
    println(s"[INFO] Java version: ${System.getProperty("java.version")}")

    ensureTopic(bootstrapServers)

    val props = new GatlingPropertiesBuilder()
      .simulationClass(classOf[SpanTrafficSimulation].getName)
      // Gatling results directory; we don't use them for analysis but keep them
      .resultsDirectory("results")

    Gatling.fromMap(props.build)
  }

  /** Creates or recreates the Kafka topic with the configured number of
    * partitions. If the topic already exists with a different partition count,
    * it is deleted and recreated.
    */
  private def ensureTopic(bootstrapServers: String): Unit = {
    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    val admin = AdminClient.create(props)
    try {
      val existing = admin.describeTopics(
        java.util.List.of(TOPIC_NAME)
      ).allTopicNames().get()
      if (!existing.isEmpty) {
        val desc = existing.get(TOPIC_NAME)
        val currentPartitions = desc.partitions().size()
        if (currentPartitions != NUM_PARTITIONS) {
          println(
            s"[INFO] Topic $TOPIC_NAME has $currentPartitions partitions, recreating with $NUM_PARTITIONS..."
          )
          admin.deleteTopics(java.util.List.of(TOPIC_NAME)).all().get()
          // Wait for deletion to propagate
          Thread.sleep(2000)
        } else {
          println(
            s"[INFO] Topic $TOPIC_NAME already exists with $NUM_PARTITIONS partitions"
          )
          return
        }
      }
    } catch {
      case _: Exception =>
      // Topic does not exist or describe failed; proceed to create
    }

    val newTopic = new NewTopic(TOPIC_NAME, NUM_PARTITIONS, REPLICATION_FACTOR)
    admin.createTopics(java.util.List.of(newTopic)).all().get()
    println(s"[INFO] Created topic $TOPIC_NAME with $NUM_PARTITIONS partitions")
    admin.close()
  }
}
