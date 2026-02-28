# Linoleum Codebase Information

## Overview
Linoleum is a runtime verification system for distributed systems using observability signals (OpenTelemetry spans) implemented with Apache Flink. It processes OTEL spans from Kafka, converts them to Linoleum events, evaluates properties using either LTLss formulas or Maude programs, and stores results in MongoDB.

## Project Structure
```
linoleum/
├── lib/                          # Main library module
│   ├── src/main/scala/io/github/demiourgoi/linoleum/
│   │   ├── SpanStreamEvaluator.scala  # Main evaluation engine
│   │   ├── config/package.scala       # Configuration classes
│   │   ├── source/package.scala       # Kafka source implementation
│   │   ├── sink/package.scala         # MongoDB sink implementation
│   │   └── messages/package.scala      # Data models and events
│   ├── src/main/java/io/github/demiourgoi/linoleum/messages/
│   │   └── *.java                     # Protobuf generated classes
│   └── build.gradle                   # Build configuration
├── docs/
│   └── design.md                      # Technical design documentation
├── devenv/                           # Development environment setup
└── Makefile                          # Build and development commands
```

## Technology Stack
- **Language**: Scala 2.13
- **Stream Processing**: Apache Flink 1.20.1
- **Data Sources**: Kafka (OTEL spans)
- **Data Sink**: MongoDB
- **Formal Verification**: Maude 3.5.0.0 (for MaudeMonitor)
- **Temporal Logic**: LTLss (via sscheck-core library)
- **Serialization**: Protocol Buffers, Kryo
- **Build System**: Gradle

## Key Dependencies
- Apache Flink (streaming, connectors)
- OpenTelemetry Protocol Buffers
- Maude Java bindings
- sscheck-core (temporal logic)
- MongoDB Flink connector
- Kafka Flink connector

## Supported Programming Languages
- **Primary**: Scala
- **Generated**: Java (protobuf)
- **Configuration**: Gradle Kotlin DSL, YAML

## Architectural Patterns
1. **Type Class Pattern**: `Property` trait enables extensible property evaluation
2. **Stream Processing**: Event-driven architecture using Flink's DataStream API
3. **Session Windows**: Trace grouping using Flink's session windows
4. **Plugin Architecture**: Support for multiple property types via type classes
5. **Formal Methods Integration**: Integration with Maude for formal verification

## Key Interfaces
- `Property[P]`: Type class for property evaluation
- `LinoleumEvent`: Sealed trait for span events (SpanStart/SpanEnd)
- `SpanInfo`: Wrapper for OTEL span data
- `EvaluatedSpans`: Result container for property evaluations

## Design Principles
1. **Extensibility**: New property types can be added via the `Property` type class
2. **Formal Verification**: Support for both runtime monitoring (LTLss) and formal verification (Maude)
3. **Observability Integration**: Built around OpenTelemetry standards
4. **State Management**: Configurable TTL for window state in MaudeMonitor
5. **Fault Tolerance**: Flink-based checkpointing and recovery