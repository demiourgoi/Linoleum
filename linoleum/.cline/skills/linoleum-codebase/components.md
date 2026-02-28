# Linoleum Components

## Core Components

### SpanStreamEvaluator
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Main processing engine that converts spans to events and evaluates properties.

**Key Responsibilities**:
1. Convert `SpanInfo` objects to `LinoleumEvent` (SpanStart/SpanEnd)
2. Group events by key (default: trace ID)
3. Apply session windows with configurable gaps
4. Evaluate properties using the `Property[P]` type class
5. Output `EvaluatedSpans` results

**Key Methods**:
- `apply(spanStream: SpanInfoStream): VerifiedSpansStream` - Main entry point
- `processWindow()` - Internal window processing logic
- `collectEvents()` - Convert spans to events, handling duplicates
- `orderEvents()` - Order events by epoch (deprecated)

**Configuration**:
- `SpanStreamEvaluatorParams[P]`: Session gap, allowed lateness, property instance
- Uses Flink's `EventTimeSessionWindows` for windowing
- Configurable TTL for state management (MaudeMonitor only)

### LinoleumSrc
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/source/package.scala`
**Purpose**: Kafka source adapter for reading OpenTelemetry spans.

**Key Responsibilities**:
1. Read `ExportTraceServiceRequest` protobuf messages from Kafka
2. Deserialize protobuf to `SpanInfo` objects
3. Extract event timestamps from span start times
4. Generate watermarks for event-time processing

**Key Classes**:
- `LinoleumSrc`: Main source class implementing `FlatMapFunction`
- `ExportTraceServiceRequestProtoDeserializer`: Protobuf deserializer

**Configuration**:
- `SourceConfig`: Kafka bootstrap servers, topics, group ID prefix
- `eventsMaxOutOfOrderness`: Watermark generation parameter
- `localFlinkEnv`: Flag for local vs cluster execution

### LinoleumSink
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/sink/package.scala`
**Purpose**: MongoDB sink for writing evaluation results.

**Key Responsibilities**:
1. Convert `EvaluatedSpans` to BSON documents
2. Batch write to MongoDB with configurable parameters
3. Provide at-least-once delivery guarantees

**Key Methods**:
- `apply(evaluatedSpans: DataStream[EvaluatedSpans]): Unit` - Sink entry point
- `buildSink(): MongoSink[EvaluatedSpans]` - Create MongoDB sink

**Configuration**:
- `SinkConfig`: MongoDB URI, database, collection, batch settings
- `MongoDbConfig`: Connection details and retry settings
- `logMaudeTerms`: Optional logging of Maude terms for debugging

### Property Type Class Hierarchy
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Abstract interface for property evaluation strategies.

**Key Components**:
- `trait Property[P]`: Type class defining evaluation interface
- `PropertySyntax.PropertyOps`: Extension methods for property instances
- `PropertyInstances`: Concrete implementations for `LinoleumFormula` and `MaudeMonitor`

**Methods**:
- `propertyName(property: P): String` - Human-readable property name
- `streamEvaluatorParams(property: P): SpanStreamEvaluatorParams[P]` - Evaluation parameters
- `evaluate(property: P)(key: String, globalStateStore: KeyedStateStore, orderedEvents: List[LinoleumEvent]): TruthValue` - Core evaluation
- `keyBy(property: P)(span: SpanInfo): String` - Key extraction (default: trace ID)
- `shouldIgnoreWindow(property: P)(windowKey: String, orderedEvents: List[LinoleumEvent]): Boolean` - Window filtering

## Property Implementations

### LinoleumFormula
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (formulas package object)
**Purpose**: Evaluate LTLss formulas on span streams.

**Key Characteristics**:
- **Stateless**: No persistence across windows
- **Trace-based**: Groups by trace ID only
- **First window only**: Only processes window containing root span
- **Tumbling windows**: Discretizes time into fixed-size ticks

**Configuration**:
- `EvaluationConfig`: Tick period, session gap, allowed lateness
- `SscheckFormulaSupplier`: LTLss formula definition
- Uses `FormulaProperty` companion object for evaluation logic

**Evaluation Process**:
1. Convert events to letters (tumbling windows)
2. Apply LTLss formula evaluation
3. Map final formula state to `TruthValue` (True/False/Undecided)

### MaudeMonitor
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (maude package object)
**Purpose**: Evaluate Maude programs with stateful monitoring.

**Key Characteristics**:
- **Stateful**: Soup term persists across windows via Flink keyed state
- **Custom grouping**: Configurable via `KeyByCriteria`
- **TTL support**: Configurable state time-to-live
- **Hook support**: Custom equality and rewriting hooks

**Configuration**:
- `EvaluationConfig`: Message rewrite bound, session gap, allowed lateness
- `StateConfig`: TTL duration, window filtering function
- Maude program path, module name, monitor OID, initial soup, property term
- Dependency programs and standard library modules

**Evaluation Process**:
1. Load Maude modules and initialize runtime
2. Parse initial soup term (or restore from state)
3. For each event: create Maude message, add to soup, rewrite
4. Evaluate final soup against property using `|=` operator
5. Save updated soup to Flink state

## Supporting Components

### Configuration Classes
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/config/package.scala`
**Purpose**: Hierarchical configuration management.

**Classes**:
- `LinoleumConfig`: Top-level configuration
  - `jobName`: Flink job name
  - `localFlinkEnv`: Local vs cluster execution flag
  - `source: SourceConfig`: Kafka source configuration
  - `sink: SinkConfig`: MongoDB sink configuration

- `SourceConfig`: Kafka source settings
  - `kafkaBootstrapServers`: Kafka broker addresses
  - `kafkaTopics`: Comma-separated topic list
  - `kafkaGroupIdPrefix`: Consumer group prefix
  - `eventsMaxOutOfOrderness`: Watermark out-of-orderness tolerance

- `SinkConfig`: Sink settings
  - `mongoDb: MongoDbConfig`: MongoDB connection details
  - `logMaudeTerms`: Enable Maude term logging for debugging

- `MongoDbConfig`: MongoDB-specific settings
  - `mongoUri`: Connection URI
  - `mongoDatabase`: Target database
  - `mongoCollection`: Target collection
  - `mongoBatchSize`, `mongoBatchIntervalMs`, `mongoMaxRetries`: Performance tuning

### Data Models
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/messages/package.scala`
**Purpose**: Core data structures and utilities.

**Classes**:
- `SpanInfo`: Wrapper for OTEL span data with utility methods
  - `hexTraceId`, `hexSpanId`: Hexadecimal string representations
  - `isRoot`: Root span detection
  - `toMaude`: Convert to Maude term representation
  - `shortToString`: Compact string representation

- `LinoleumEvent`: Sealed trait for span events
  - `SpanStart`: Event for span beginning
  - `SpanEnd`: Event for span completion
  - `epochUnixNano`: Event timestamp in nanoseconds
  - `toMaude(oid: String)`: Maude message representation

- `EvaluatedSpans`: Evaluation result container
  - `key`: Grouping key (trace ID or custom)
  - `startTimeUnixNano`: Window start time
  - `propertyName`: Evaluated property name
  - `truthValue`: Evaluation result (True/False/Undecided)
  - `toBsonDocument`: Convert to MongoDB BSON document

### Maude Integration
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (maude package object)
**Purpose**: Maude runtime integration and term conversion.

**Key Components**:
- `MaudeModules`: Maude runtime management and module loading
- `spanToMaude()`: Convert OTEL spans to Maude terms
- `KeyByCriteria`: Trait for custom grouping strategies
  - `KeyByTraceId`: Default grouping by trace ID
  - `KeyByStringSpanAttribute`: Group by span attribute value

**Maude Term Format**:
- Span OID: `$traceId/$spanId`
- Event OID: `$traceId/$spanId/$index`
- Attributes as key-value pairs
- Events as nested objects within spans

### Utility Functions
**Location**: Various package objects in `SpanStreamEvaluator.scala`
**Purpose**: Shared utility functions.

**Key Utilities**:
- `TimeUtils`: Time conversion between nanoseconds and milliseconds
- `byteString2HexString`: Convert ByteString to hexadecimal string
- `stringValueToMaude`: String escaping for Maude compatibility
- `anyValueToMaude`: Convert OTEL AnyValue to Maude string representation

## Component Relationships

### Dependency Graph
```
LinoleumConfig
    ├── SourceConfig
    │   └── LinoleumSrc
    │       └── SpanStreamEvaluator
    │           ├── Property[P]
    │           │   ├── LinoleumFormula
    │           │   └── MaudeMonitor
    │           └── ProcessWindow
    └── SinkConfig
        └── LinoleumSink
```

### Data Flow
1. **Configuration** → `Linoleum.execute()` creates Flink environment
2. **Source** → `LinoleumSrc` reads from Kafka, produces `SpanInfoStream`
3. **Processing** → `SpanStreamEvaluator` converts to events, applies windows
4. **Evaluation** → `Property[P]` implementation evaluates property
5. **Sink** → `LinoleumSink` writes results to MongoDB

### State Management
- **Flink Keyed State**: Used by `MaudeMonitor` for soup term persistence
- **State TTL**: Configurable cleanup of stale state
- **Window State**: Per-key state within session windows
- **Global State**: Accessed via `context.globalState()` in `ProcessWindow`

## Extension Points

### Custom Key Grouping
Implement `KeyByCriteria` trait:
```scala
case class CustomKeyBy(attribute: String) extends KeyByCriteria {
  override def keyBy(span: SpanInfo): String = {
    // Extract custom key from span attributes
    span.getSpan.getAttributesList.asScala
      .find(_.getKey == attribute)
      .map(kv => anyValueToMaude(kv.getValue))
      .getOrElse(span.hexTraceId)
  }
}
```

### Custom Property Types
Implement `Property[CustomProperty]`:
```scala
implicit val customProperty: Property[CustomProperty] = 
  new Property[CustomProperty] with Serializable {
    override def propertyName(property: CustomProperty): String = property.name
    override def streamEvaluatorParams(property: CustomProperty): SpanStreamEvaluatorParams[CustomProperty] = 
      SpanStreamEvaluatorParams(property, property.sessionGap, property.allowedLateness)
    override def evaluate(property: CustomProperty)(key: String, globalStateStore: KeyedStateStore, orderedEvents: List[LinoleumEvent]): TruthValue = {
      // Custom evaluation logic
    }
    override def keyBy(property: CustomProperty)(span: SpanInfo): String = 
      property.keyBy.keyBy(span)
    override def shouldIgnoreWindow(property: CustomProperty)(windowKey: String, orderedEvents: List[LinoleumEvent]): Boolean = 
      property.shouldIgnoreWindow(windowKey, orderedEvents)
  }
```

### Custom Maude Hooks
```scala
MaudeMonitor(
  eqHooks = List(("customEqOp", () => new CustomEqHook())),
  rlHooks = List(("customRlOp", () => new CustomRlHook())),
  // ... other parameters
)
```

## Testing Components

### Test Structure
**Location**: `lib/src/test/scala/io/github/demiourgoi/linoleum/`

**Test Files**:
- `evaluator/SpanStreamEvaluatorTest.scala`: Main evaluator tests
- `messages/LinoleumSpanInfoTest.scala`: SpanInfo utility tests

**Test Patterns**:
1. **Unit Tests**: Isolated component testing
2. **Integration Tests**: Flink pipeline with embedded components
3. **Property Tests**: Verification of evaluation logic
4. **Serialization Tests**: Protobuf and BSON conversion

### Test Utilities
- Flink test utilities for stream processing
- Embedded Kafka for source testing
- Embedded MongoDB for sink testing
- Maude test programs for verification