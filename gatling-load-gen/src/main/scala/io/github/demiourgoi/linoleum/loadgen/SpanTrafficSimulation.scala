package io.github.demiourgoi.linoleum.loadgen

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder

import org.apache.kafka.clients.producer.ProducerConfig

import ru.tinkoff.gatling.kafka.Predef._
import ru.tinkoff.gatling.kafka.protocol.KafkaProtocol

import scala.concurrent.duration._

/** Gatling simulation that sends protobuf-serialized OTEL span traffic to
  * Kafka with a staircase ramp-up pattern.
  *
  * Plateaus: 100 → 500 → 1000 → 5000 → 10000 messages per second.
  * Each ramp lasts 30 seconds, each plateau hold lasts 60 seconds.
  *
  * Phase schedule (seconds from start):
  *   [0, 30)    ramp 0→100
  *   [30, 90)   hold 100
  *   [90, 120)  ramp 100→500
  *   [120, 180) hold 500
  *   [180, 210) ramp 500→1000
  *   [210, 270) hold 1000
  *   [270, 300) ramp 1000→5000
  *   [300, 360) hold 5000
  *   [360, 390) ramp 5000→10000
  *   [390, 450) hold 10000
  */
class SpanTrafficSimulation extends Simulation {

  // --- Kafka protocol: raw protobuf bytes to the otlp_spans topic ---
  private val kafkaProtocol: KafkaProtocol = kafka
    .topic("otlp_spans")
    .properties(
      Map[String, Object](
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG      -> sys.props.getOrElse("kafka.bootstrap.servers", "localhost:9092"),
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG   -> "org.apache.kafka.common.serialization.StringSerializer",
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> "org.apache.kafka.common.serialization.ByteArraySerializer",
        ProducerConfig.ACKS_CONFIG                   -> "0",
        ProducerConfig.LINGER_MS_CONFIG              -> "5",
        ProducerConfig.BATCH_SIZE_CONFIG             -> "16384",
      ),
    )

  // --- Plateau transition logging ---
  private val plateauPhases = Seq(
    (0,   "ramping to 100 msg/s"),
    (30,  "plateau: 100 msg/s"),
    (90,  "ramping to 500 msg/s"),
    (120, "plateau: 500 msg/s"),
    (180, "ramping to 1000 msg/s"),
    (210, "plateau: 1000 msg/s"),
    (270, "ramping to 5000 msg/s"),
    (300, "plateau: 5000 msg/s"),
    (360, "ramping to 10000 msg/s"),
    (390, "plateau: 10000 msg/s"),
  )

  @volatile private var lastLoggedPhase = -1
  @volatile private var simulationStartMs = 0L
  private val startOnce = new java.util.concurrent.atomic.AtomicBoolean(false)

  // --- Scenario: one user = one ExportTraceServiceRequest message ---
  private val scn: ScenarioBuilder = scenario("Span traffic")
    .exec { session =>
      // Capture start time on first user and log plateau transitions
      if (startOnce.compareAndSet(false, true)) {
        simulationStartMs = System.currentTimeMillis()
        println("[INFO] Span traffic generation started")
      }
      val start = simulationStartMs
      if (start > 0) {
        val elapsedSec = ((System.currentTimeMillis() - start) / 1000).toInt
        var i = plateauPhases.size - 1
        while (i >= 0 && elapsedSec >= plateauPhases(i)._1) {
          if (i > lastLoggedPhase) {
            this.synchronized {
              if (i > lastLoggedPhase) {
                lastLoggedPhase = i
                println(s"[INFO] ${plateauPhases(i)._2}")
              }
            }
          }
          i -= 1
        }
      }
      session
    }
    .exec { session =>
      val request = SpanGenerator.generateRequest()
      val key     = java.util.UUID.randomUUID().toString
      session
        .set("kafkaKey", key)
        .set("kafkaValue", request.toByteArray)
    }
    .exec(
      kafka("Send spans")
        .send[String, Array[Byte]]("#{kafkaKey}", "#{kafkaValue}")
    )

  // --- Injection profile ---
  setUp(
    scn.inject(
      rampUsersPerSec(0).to(100).during(30.seconds),
      constantUsersPerSec(100).during(60.seconds),
      rampUsersPerSec(100).to(500).during(30.seconds),
      constantUsersPerSec(500).during(60.seconds),
      rampUsersPerSec(500).to(1000).during(30.seconds),
      constantUsersPerSec(1000).during(60.seconds),
      rampUsersPerSec(1000).to(5000).during(30.seconds),
      constantUsersPerSec(5000).during(60.seconds),
      rampUsersPerSec(5000).to(10000).during(30.seconds),
      constantUsersPerSec(10000).during(60.seconds),
    )
  ).protocols(kafkaProtocol)
}
