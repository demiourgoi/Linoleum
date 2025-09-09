package io.github.demiourgoi.linoleum.ltlss

import java.time.Duration

// TODO fields and YAML serde
package object config {
  case class SourceConfig(
    kafkaBootstrapServers: String = "localhost:9092",
    kafkaTopics: String = "otlp_spans",
    kafkaGroupIdPrefix: String = "linolenum-cg",
    eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500),
  )

  case class SinkConfig(
    mongoDb: MongoDbConfig = MongoDbConfig()
  )

  case class MongoDbConfig(
    mongoUri: String = "mongodb://localhost:27017",
    mongoDatabase: String = "linoleum",
    mongoCollection: String = "evaluatedTraces",
    mongoBatchSize: Int = 10,
    mongoBatchIntervalMs: Long = 1000L,
    mongoMaxRetries: Int = 3
  )

  case class EvaluationConfig(
    tickPeriod: Duration,
    sessionGap: Duration,
    allowedLateness: Duration = Duration.ofMillis(0),
  )


  case class LinoleumConfig(
    jobName: String,
    localFlinkEnv: Boolean,
    evaluation: EvaluationConfig,
    source: SourceConfig = SourceConfig(),
    sink: SinkConfig = SinkConfig()
  )
}
