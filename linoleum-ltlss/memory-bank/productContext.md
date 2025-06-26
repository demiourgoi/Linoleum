# Linoleum LTLss Product Context

## Purpose
- Runtime verification system for distributed applications using OpenTelemetry traces
- Detects temporal logic violations in near real-time
- Target users: SREs and distributed systems engineers needing system-level guarantees

## Key Value Propositions
- Leverages existing telemetry infrastructure (no instrumentation changes)
- Provides formal verification of temporal properties
- Scales horizontally using Apache Flink

## Core Workflows
1. **Trace Ingestion**: Consumes OpenTelemetry spans from Kafka
2. **Trace Processing**: Groups spans by trace ID and linearizes events
3. **Formula Evaluation**: Applies LTLss formulas to event sequences
4. **Result Storage**: Persists evaluation results in MongoDB
5. **Monitoring**: Provides interfaces for result visualization

## Example Use Cases
- Verifying eventual consistency in distributed databases
- Detecting workflow violations in microservice architectures
- Monitoring SLAs in asynchronous processing systems
