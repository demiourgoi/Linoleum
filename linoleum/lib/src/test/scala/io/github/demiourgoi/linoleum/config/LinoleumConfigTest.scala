package io.github.demiourgoi.linoleum.config

import java.nio.file.Paths
import java.time.Duration
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable.Specification

@RunWith(classOf[JUnitRunner])
class LinoleumConfigTest extends Specification {

  "LinoleumConfig.fromPath" should {
    "load configuration from a valid YAML file" in {
      // Get the path to the test resource
      val resourceUrl = getClass.getResource("/config-test.yaml")
      resourceUrl must not beNull
      
      val configPath = Paths.get(resourceUrl.toURI)
      
      // Load the configuration
      val config = LinoleumConfig.fromPath(configPath)
      
      // Verify the main configuration
      config.jobName must_== "test-job"
      config.localFlinkEnv must beTrue
      
      // Verify source configuration
      config.source.kafkaBootstrapServers must_== "kafka:9092"
      config.source.kafkaTopics must_== "test-spans"
      config.source.kafkaGroupIdPrefix must_== "test-cg"
      config.source.eventsMaxOutOfOrderness must_== Duration.ofSeconds(1)
      
      // Verify sink configuration
      config.sink.logMaudeTerms must beTrue
      
      // Verify MongoDB configuration
      config.sink.mongoDb.mongoUri must_== "mongodb://test:27017"
      config.sink.mongoDb.mongoDatabase must_== "test-db"
      config.sink.mongoDb.mongoCollection must_== "test-collection"
      config.sink.mongoDb.mongoBatchSize must_== 20
      config.sink.mongoDb.mongoBatchIntervalMs must_== 500L
      config.sink.mongoDb.mongoMaxRetries must_== 5
    }

    "apply default values when optional fields are omitted" in {
      // Get the path to the minimal test resource
      val resourceUrl = getClass.getResource("/config-minimal.yaml")
      resourceUrl must not beNull
      
      val configPath = Paths.get(resourceUrl.toURI)
      
      // Load the configuration
      val config = LinoleumConfig.fromPath(configPath)
      
      // Verify the main configuration
      config.jobName must_== "minimal-job"
      config.localFlinkEnv must beFalse
      
      // Verify source configuration uses defaults
      config.source.kafkaBootstrapServers must_== "localhost:9092"
      config.source.kafkaTopics must_== "otlp_spans"
      config.source.kafkaGroupIdPrefix must_== "linolenum-cg"
      config.source.eventsMaxOutOfOrderness must_== Duration.ofMillis(500)
      
      // Verify sink configuration uses defaults
      config.sink.logMaudeTerms must beFalse
      
      // Verify MongoDB configuration uses defaults
      config.sink.mongoDb.mongoUri must_== "mongodb://localhost:27017"
      config.sink.mongoDb.mongoDatabase must_== "linoleum"
      config.sink.mongoDb.mongoCollection must_== "evaluatedSpans"
      config.sink.mongoDb.mongoBatchSize must_== 10
      config.sink.mongoDb.mongoBatchIntervalMs must_== 1000L
      config.sink.mongoDb.mongoMaxRetries must_== 3
    }

    "throw an exception for non-existent file" in {
      val nonExistentPath = Paths.get("/non/existent/config.yaml")
      LinoleumConfig.fromPath(nonExistentPath) must throwA[Exception]
    }

    "throw an exception for invalid YAML content" in {
      val tempFile = java.io.File.createTempFile("invalid", ".yaml")
      tempFile.deleteOnExit()
      
      // Write invalid YAML content
      val writer = new java.io.PrintWriter(tempFile)
      writer.write("invalid: yaml: content: [unclosed")
      writer.close()
      
      LinoleumConfig.fromPath(tempFile.toPath) must throwA[Exception]
    }

    "throw an exception when required fields are missing" in {
      val tempFile = java.io.File.createTempFile("missing", ".yaml")
      tempFile.deleteOnExit()
      
      // Write YAML without required fields
      val writer = new java.io.PrintWriter(tempFile)
      writer.write("source:\n  kafkaBootstrapServers: \"localhost:9092\"")
      writer.close()
      
      LinoleumConfig.fromPath(tempFile.toPath) must throwA[Exception]
    }
  }
}
