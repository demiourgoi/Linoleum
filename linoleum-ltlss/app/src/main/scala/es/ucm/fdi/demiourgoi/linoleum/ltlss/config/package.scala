package es.ucm.fdi.demiourgoi.linoleum.ltlss

import java.time.Duration

package object config {
  // TODO fields and YAML serde
  case class LinoleumConfig(
    localFlinkEnv: Boolean,
    kafkaBootstrapServers: String = "localhost:9092",
    kafkaTopics: String = "otlp_spans",
    kafkaGroupIdPrefix: String = "linolenum-cg",
    eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500),
    mongoUri: String = "mongodb://localhost:27017",
    mongoDatabase: String = "linoleum",
    mongoCollection: String = "evaluatedTraces",
    mongoBatchSize: Int = 10,
    mongoBatchIntervalMs: Long = 1000L,
    mongoMaxRetries: Int = 3
  )
}
