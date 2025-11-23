package io.github.demiourgoi.linoleum

import java.time.Duration

// TODO fields and YAML serde
package object config {
  case class SourceConfig(
    kafkaBootstrapServers: String = "localhost:9092",
    kafkaTopics: String = "otlp_spans",
    kafkaGroupIdPrefix: String = "linolenum-cg",
    eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500),
  )

  /**
   * @param logMaudeTerms if true then the driver will write on `SinkConfig.MaudeTermLogPath` 
   * all the spans read from the source during the job execution, in the following plain text format:
   * - Each span is written in a line as a Maude term as formatted by `LinoleumSpanInfo.toMaude`
  */
  case class SinkConfig(
    mongoDb: MongoDbConfig = MongoDbConfig(),
    logMaudeTerms: Boolean = false
  )
  object SinkConfig {
    val MaudeTermLogPath = "./maude_terms"
  }

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
