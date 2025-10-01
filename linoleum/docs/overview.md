# Linoleum: A Runtime Verification Tool for Distributed Systems

Linoleum is a runtime verification tool designed to check system properties of distributed applications using telemetry signals, specifically distributed traces. Based on my analysis of the documentation and source code, here's what this package does:

## Core Purpose

Linoleum is a near real-time system-level model-based trace-checking tool that:

1. Consumes OpenTelemetry (OTEL) spans from distributed systems
2. Evaluates temporal logic formulas against these traces
3. Stores the evaluation results for monitoring and analysis

## Key Components and Architecture

The system consists of several key components:

1. **Formula Definition**: Properties to verify are written in LTLss (Linear Temporal Logic with timeouts), a timed temporal logic that extends propositional LTL. These formulas evaluate sequences of events (spans) into true, false, or inconclusive values.

2. **Span Processing Pipeline**:
   - Spans are consumed from Kafka (populated by the OpenTelemetry collector)
   - Spans are grouped by trace ID and ordered by timestamp
   - The span tree is linearized into a sequence of events (SpanStart and SpanEnd)
   - These events are discretized into "letters" (time windows) for formula evaluation

3. **Evaluation Engine**:
   - Built on Apache Flink for horizontal scalability and fault tolerance
   - Uses session windows to determine when enough spans have been collected for a trace
   - Evaluates the LTLss formula against the sequence of letters
   - Stores results in MongoDB

4. **Testing Framework**:
   - Uses Maude (a specification language) to model distributed systems
   - Generates random traces through simulation
   - Feeds these traces to Linoleum for verification

## Example Use Case

The documentation demonstrates Linoleum with "Luego," a fictitious asynchronous HTTP proxy that wraps slow synchronous APIs. The system verifies properties like "if a client has a task assigned, then eventually the database will have a result for that task" by monitoring the spans generated during system execution.

## Technical Implementation

- Written in Scala, using Apache Flink for stream processing
- Integrates with standard OpenTelemetry protocols
- Uses MongoDB for storing evaluation results
- Supports horizontal scaling for processing large volumes of traces

## Current Status and Future Work

The project appears to be in early development stages with plans to:

1. Generalize the data structures for broader application
2. Explore alternative approaches to trace linearization
3. Support different kinds of properties and span structures
4. Potentially replace LTLss with direct Maude specifications to avoid translation gaps

Linoleum represents an innovative approach to runtime verification by leveraging existing telemetry infrastructure (OpenTelemetry) that's already widely used in modern distributed systems, making it potentially valuable for software engineers working with microservice architectures.
