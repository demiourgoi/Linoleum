# Codebase Information

## Overview

**Project name:** `gatling-load-gen`  
**Group ID:** `io.github.demiourgoi`  
**Version:** `0.1.0-SNAPSHOT`  
**Description:** Linoleum Kafka load generator  
**Parent project:** [Linoleum](../../README.md) — an experiment for using observability signals for runtime verification of distributed systems.

## Technology Stack

| Category | Technology | Version |
|---|---|---|
| Language | Scala | 2.13.15 |
| Build | Gradle | 8.10.2 |
| JVM | Java | 17 (toolchain) |
| Load testing | Gatling | 3.9.5 |
| Kafka integration | gatling-kafka-plugin | 0.12.1 |
| Serialization | Protobuf (Java) | 3.25.6 |
| Avro support | avro4s | 4.1.1 |

## Directory Structure

```
gatling-load-gen/
├── build.gradle              # Build configuration and dependencies
├── settings.gradle.kts       # Gradle settings (root project name)
├── Makefile                  # Convenience targets: build, run, clean
├── gradlew                   # Gradle wrapper script
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    └── main/
        └── scala/
            └── io/github/demiourgoi/linoleum/loadgen/
                ├── Main.scala                   # Entry point
                ├── SpanGenerator.scala          # OTEL span protobuf generator
                └── SpanTrafficSimulation.scala  # Gatling simulation definition
```

## Supported Languages

- **Scala** — all source code is written in Scala 2.13

## Development Setup

1. Publish the `linoleum` library to local Maven: `cd linoleum && make release`
2. Start Kafka and dependencies: `cd linoleum && make compose/start`
3. Build: `make build`
4. Run: `make run`

Kafka bootstrap servers default to `localhost:9092` and can be overridden via `-Dkafka.bootstrap.servers=host:port`.

## Internal vs. External Dependencies

- **Internal:** `io.github.demiourgoi:linoleum_2.13:0.2.0-SNAPSHOT` — provides OTEL protobuf classes (`ExportTraceServiceRequest`, `Span`, `KeyValue`, `AnyValue`, etc.). Excludes Flink, Twitter, MongoDB, and other unrelated transitive deps.
- **External:** Gatling, gatling-kafka-plugin, Protobuf, Avro. Confluent repo required for transitive deps of gatling-kafka-plugin.
