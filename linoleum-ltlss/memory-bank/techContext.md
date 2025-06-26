# Technology Stack

## Core Components
- **Language**: Scala 2.13 (LTLss DSL)
- **Stream Processing**: Apache Flink (stateful stream processing)
- **Storage**: MongoDB (result storage)
- **Telemetry**: OpenTelemetry Protocol (OTLP)

## Key Dependencies
- **Stream Processing**:
  - Flink DataStrean API
- **Verification**:
  - Maude model checker
  - LTLss formula evaluator
- **Utilities**:
  - slf4j and Log4j2 for logging
  - Protocol buffers for serialization

## Configuration

Example configuration

- **Flink**:
  - Checkpoint interval: 10s
  - Parallelism: 4 (configurable)
- **Kafka**:
  - Consumer group: "linoleum-ltlss"
  - Auto offset reset: "latest"
- **MongoDB**:
  - Connection pool: 10
  - Write concern: "acknowledged"

## Development Environment
- **Build Tool**: Gradle
- **Testing**:
  - Specs2
  - Docker compose for integration tests
- **Testing Infrastructure**:
  - Maude 3.2+ for model-based trace generation
  - Specs2 testing framework integration
