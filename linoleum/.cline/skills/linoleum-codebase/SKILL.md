---
name: linoleum-codebase
description: Understand the design of the 'linoleum' package, and how the codebase works. Use that information when analysing the code, making code changes, and proposing new technical designs
---

# Linoleum Codebase Skill

This skill provides comprehensive knowledge about the Linoleum runtime verification system for distributed systems using OpenTelemetry spans and Apache Flink.

## Overview

Linoleum is a runtime verification system that processes OpenTelemetry (OTEL) spans from Kafka, converts them to Linoleum events, evaluates properties using either LTLss formulas or Maude programs, and stores results in MongoDB.

## Key Concepts

### Data Model
- **SpanInfo**: Wrapper for OTEL span data with trace/span IDs and metadata
- **LinoleumEvent**: Sealed trait with `SpanStart` and `SpanEnd` events
- **EvaluatedSpans**: Result container for property evaluations with truth values (True/False/Undecided)

### Property Evaluation System
Linoleum supports two types of property evaluation:

1. **LinoleumFormula**: LTLss-based formulas evaluated on tumbling windows
   - Stateless evaluation
   - Only processes first session window (containing root span)
   - Groups spans by trace ID

2. **MaudeMonitor**: Maude-based programs with stateful monitoring
   - State persists across session windows via Flink keyed state
   - Configurable Time-To-Live (TTL) for state
   - Custom grouping keys via `KeyByCriteria`
   - Supports equality and rewriting hooks

### Architecture Components

#### Data Flow Pipeline
```
Kafka (OTEL spans) → LinoleumSrc → SpanStreamEvaluator → LinoleumSink → MongoDB
```

#### Core Components
- **LinoleumSrc**: Kafka source adapter reading `ExportTraceServiceRequest` protobufs
- **SpanStreamEvaluator**: Main processing engine converting spans to events and evaluating properties
- **LinoleumSink**: MongoDB sink writing `EvaluatedSpans` as BSON documents
- **Property Type Class**: Abstraction layer for different property evaluation strategies

#### Configuration
- **LinoleumConfig**: Main configuration with job name, local vs cluster mode, source/sink configs
- **SourceConfig**: Kafka connection parameters
- **SinkConfig**: MongoDB connection and logging options

## Directory Structure

```
linoleum/
├── lib/                          # Main library module
│   ├── src/main/scala/io/github/demiourgoi/linoleum/
│   │   ├── SpanStreamEvaluator.scala  # Main evaluation engine
│   │   ├── config/package.scala       # Configuration classes
│   │   ├── source/package.scala       # Kafka source implementation
│   │   ├── sink/package.scala         # MongoDB sink implementation
│   │   ├── messages/package.scala     # Data models and events
│   │   ├── formulas/package.scala     # LTLss formula support
│   │   └── maude/package.scala         # Maude integration
│   ├── src/main/java/io/github/demiourgoi/linoleum/messages/
│   │   └── *.java                     # Protobuf generated classes
│   ├── src/main/resources/maude/linoleum/
│   │   └── trace.maude                # Maude trace definitions
│   └── build.gradle                   # Build configuration
├── docs/
│   └── design.md                      # Technical design documentation
├── devenv/                           # Development environment (Docker compose)
└── Makefile                          # Build and development commands
```

## Development Patterns

### Type Class Pattern
The `Property[P]` type class enables extensible property evaluation:
```scala
trait Property[P] {
  def propertyName(property: P): String
  def streamEvaluatorParams(property: P): SpanStreamEvaluatorParams[P]
  def evaluate(property: P)(key: String, globalStateStore: KeyedStateStore, 
                          orderedEvents: List[LinoleumEvent]): TruthValue
  def keyBy(property: P)(span: SpanInfo): String = span.hexTraceId
  def shouldIgnoreWindow(property: P)(windowKey: String, 
                                    orderedEvents: List[LinoleumEvent]): Boolean = false
}
```

### Session Window Processing
- Uses Flink's `EventTimeSessionWindows` for trace grouping
- Events ordered by epoch (start time for SpanStart, end time for SpanEnd)
- Configurable session gap and allowed lateness
- Keyed state management for MaudeMonitor

### Maude Integration
- Maude programs loaded from resources (`maude/` directory)
- Soup term represents monitor state across windows
- Equality and rewriting hooks for custom operations
- Thread-safe Maude runtime with synchronization

## Key Interfaces and Classes

### `SpanStreamEvaluator`
Main processing class that:
1. Converts spans to `SpanStart`/`SpanEnd` events
2. Groups by key (default: trace ID)
3. Applies session windows
4. Evaluates properties using the `Property` type class
5. Outputs `EvaluatedSpans` to sink

### `LinoleumEvent` Hierarchy
```scala
sealed trait LinoleumEvent {
  def epochUnixNano: Long  // Event timestamp in nanoseconds
  def span: SpanInfo
  def toMaude(oid: String): String  // Maude representation
}
case class SpanStart(span: SpanInfo) extends LinoleumEvent
case class SpanEnd(span: SpanInfo) extends LinoleumEvent
```

### Configuration Classes
- `LinoleumConfig`: Top-level configuration
- `SourceConfig`: Kafka source settings
- `SinkConfig`: MongoDB sink and logging settings
- `MongoDbConfig`: MongoDB connection details

## How to Write and Run Tests

### Test Structure
Tests are in `lib/src/test/scala/io/github/demiourgoi/linoleum/`:
- `evaluator/SpanStreamEvaluatorTest.scala`: Main evaluator tests
- `messages/LinoleumSpanInfoTest.scala`: SpanInfo utility tests

### Running Tests
```bash
# Run all tests
make test

# Run specific test class
./gradlew :lib:test --tests "io.github.demiourgoi.linoleum.evaluator.SpanStreamEvaluatorTest"
```

### Test Patterns
1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test Flink pipeline with embedded Kafka/MongoDB
3. **Property Tests**: Verify property evaluation logic

## Common Development Tasks

### Adding a New Property Type
1. Implement the `Property[P]` type class for your property type
2. Define property-specific configuration parameters
3. Implement evaluation logic in the `evaluate` method
4. Add to `PropertyInstances` companion object
5. Create factory methods in `Linoleum` object

### Modifying Event Processing
1. Update `SpanStreamEvaluator.processWindow()` for window logic changes
2. Modify `LinoleumEvent` hierarchy for new event types
3. Update `spanToMaude()` for Maude representation changes

### Changing Data Sources/Sinks
1. Update `LinoleumSrc` for new source adapters
2. Update `LinoleumSink` for new sink adapters
3. Modify configuration classes accordingly

## Configuration Guidelines

### Kafka Configuration
- Default bootstrap servers: `localhost:9092`
- Default topic: `otlp_spans`
- Event time extraction from span start time
- Configurable out-of-orderness and idleness

### MongoDB Configuration
- Default URI: `mongodb://localhost:27017`
- Default database: `linoleum`
- Default collection: `evaluatedSpans`
- Batch size and interval configurable

### Flink Configuration
- Local mode with web UI: `localhost:8081`
- Serialization using Kryo with protobuf support
- Checkpointing and state backend configurable

## Maude Integration Details

### Trace Representation
Spans are converted to Maude terms using `spanToMaude()`:
- Span OID format: `$traceId/$spanId`
- Event OID format: `$traceId/$spanId/$index`
- Attributes converted to Maude key-value pairs

### Monitor Configuration
`MaudeMonitor` requires:
- Program resource path (e.g., `"maude/lotrbot_imagegen_safety.maude"`)
- Module name containing monitor class
- Monitor object OID
- Initial soup term
- Property term for evaluation
- Optional hooks for custom operations

### State Management
- Soup term stored in Flink keyed state
- TTL configurable via `StateConfig`
- State cleaned up after TTL expiration
- Custom window filtering for TTL edge cases

## Performance Considerations

### Window Processing
- Session windows based on trace activity gaps
- Configurable allowed lateness for late events
- State TTL for memory management in MaudeMonitor

### Maude Performance
- Maude runtime not thread-safe (uses synchronization)
- Rewrite bounds configurable per message
- Soup terms can grow large for long-running traces

### Serialization
- Protobuf serialization with Kryo
- Custom serializers for Maude terms
- BSON conversion for MongoDB storage

## Common Issues and Solutions

### Duplicate Span Handling
- `SpanStreamEvaluator` detects and skips duplicate spans by span ID
- Assumes identical spans have same span ID

### Root Span Detection
- Root span has empty `parentSpanId` field
- Only first window (containing root span) processed for `LinoleumFormula`
- Multiple root spans trigger `EventCollectionMultipleRootSpansError`

### Maude String Handling
- JSON strings escaped with `StringEscapeUtils.escapeJson()`
- Nested quotes handled to avoid Maude parsing issues
- See issue #15 for details

### State TTL Limitations
- Cannot distinguish between first initialization and TTL cleanup
- Custom `shouldIgnoreWindow` function for domain-specific filtering

## Extension Points

### Custom Key Grouping
Implement `KeyByCriteria` trait:
```scala
case class KeyByStringSpanAttribute(key: String) extends KeyByCriteria {
  override def keyBy(span: SpanInfo): String = {
    // Extract attribute or fallback to trace ID
  }
}
```

### Custom Maude Hooks
```scala
MaudeMonitor(
  eqHooks = List(("operatorName", () => new MyEqHook())),
  rlHooks = List(("operatorName", () => new MyRlHook()))
)
```

### New Property Types
Implement `Property[MyProperty]` type class with:
- Property-specific configuration
- Evaluation logic
- Key grouping strategy
- Window filtering logic

## References

- [OpenTelemetry Protocol](https://github.com/open-telemetry/opentelemetry-proto)
- [Apache Flink Documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/)
- [Maude System](http://maude.cs.illinois.edu/)
- [sscheck Library](https://github.com/demiourgoi/sscheck-core)
- [DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md) for setup and testing
- [docs/design.md](./docs/design.md) for technical design details