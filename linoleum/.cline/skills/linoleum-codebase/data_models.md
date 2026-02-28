# Linoleum Data Models

## Core Data Structures

### SpanInfo
**Location**: Generated from protobuf in `lib/src/main/java/io/github/demiourgoi/linoleum/messages/SpanInfo.java`
**Purpose**: Wrapper for OpenTelemetry span data with utility methods.

**Key Fields**:
- `span`: `io.opentelemetry.proto.trace.v1.Span` - Raw OTEL span
- `scope`: `io.opentelemetry.proto.common.v1.InstrumentationScope` - Instrumentation scope
- `scopeSchemaUrl`: `String` - Schema URL for scope
- `resource`: `io.opentelemetry.proto.resource.v1.Resource` - Resource information
- `resourceSchemaUrl`: `String` - Schema URL for resource

**Utility Methods** (via `LinoleumSpanInfo` implicit class):
- `hexTraceId`: Hexadecimal string representation of trace ID
- `hexSpanId`: Hexadecimal string representation of span ID
- `isRoot`: Boolean indicating if span has no parent (root span)
- `spanId`: UTF-8 string representation of span ID
- `isNamed(name: String)`: Check if span has given name
- `shortToString`: Compact string representation for logging
- `toMaude`: Convert to Maude term representation

**Example**:
```scala
val spanInfo: SpanInfo = // from Kafka source
println(spanInfo.hexTraceId)  // "1a164375b7463f1e8ddfe4a55e01cb5d"
println(spanInfo.isRoot)      // true if no parent span
println(spanInfo.isNamed("http.request"))  // check span name
```

### LinoleumEvent Hierarchy
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/messages/package.scala`
**Purpose**: Represent span lifecycle events for temporal analysis.

**Sealed Trait**:
```scala
sealed trait LinoleumEvent {
  def epochUnixNano: Long  // Event timestamp in nanoseconds
  def span: SpanInfo      // Associated span
  def shortToString: String  // Compact representation
  def toMaude(oid: String): String  // Maude representation
}
```

**Case Classes**:
1. **SpanStart** - Beginning of a span
   - `epochUnixNano`: Uses `span.getSpan.getStartTimeUnixNano`
   - `toMaude`: Generates `spanStart($oid, ${span.toMaude})`

2. **SpanEnd** - Completion of a span
   - `epochUnixNano`: Uses `span.getSpan.getEndTimeUnixNano`
   - `toMaude`: Generates `spanEnd($oid, ${span.toMaude})`

**Event Ordering**:
```scala
implicit val linoleumEventOrdering: Ordering[LinoleumEvent] =
  Ordering[Long].on(_.epochUnixNano)
```
Events are ordered by their epoch (start time for SpanStart, end time for SpanEnd).

### EvaluatedSpans
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Container for property evaluation results.

**Fields**:
- `key: String` - Grouping key (trace ID or custom attribute value)
- `startTimeUnixNano: Long` - Window start time in nanoseconds
- `propertyName: String` - Name of evaluated property
- `truthValue: TruthValue` - Evaluation result (True/False/Undecided)

**Methods**:
- `toBsonDocument`: Convert to MongoDB BSON document for storage
  - `key`: BSON string
  - `startDate`: BSON DateTime (converted from nanoseconds to milliseconds)
  - `evaluationDate`: BSON DateTime (current time)
  - `propertyName`: BSON string
  - `truthValue`: BSON string representation

**Example BSON Document**:
```json
{
  "key": "1a164375b7463f1e8ddfe4a55e01cb5d",
  "startDate": ISODate("2024-01-01T00:00:00Z"),
  "evaluationDate": ISODate("2024-01-01T00:00:05Z"),
  "propertyName": "ResponseTimeUnder1s",
  "truthValue": "True"
}
```

### TruthValue ADT
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Three-valued logic for property evaluation results.

**Values**:
- `True`: Property holds
- `False`: Property violated
- `Undecided`: Property cannot be determined (e.g., incomplete trace)

**Serialization**: Converted to string for BSON storage:
- `True.toString()` → `"True"`
- `False.toString()` → `"False"`
- `Undecided.toString()` → `"Undecided"`

## Maude Data Representations

### Maude Term Format
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (maude package object)
**Purpose**: Convert OTEL spans to Maude terms for formal verification.

**Span Representation**:
```maude
< span("$traceId/$spanId") : Span |
  traceId : "$traceId",
  spanId : "$spanId",
  parentSpanId : "$parentSpanId",
  name : "$spanName",
  startTimeUnixNano : $startTime,
  endTimeUnixNano : $endTime,
  attributes : $attributes,
  events : $events
>
```

**Attribute Conversion**:
Attributes are converted to Maude key-value pairs:
```maude
["$key", "$value"]
```

**Event Representation**:
Events within spans are represented as:
```maude
< event("$traceId/$spanId/$index") : Event |
  timeUnixNano : $eventTime,
  name : "$eventName",
  attributes : $eventAttributes
>
```

**Value Conversion**:
- String values: JSON-escaped strings
- Boolean values: `true` or `false`
- Integer values: Numeric representation
- Double values: Floating point representation
- Bytes values: Hexadecimal string representation
- Arrays/KVLists: Currently unsupported (converted to placeholder strings)

### Maude Message Format
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/messages/package.scala`
**Purpose**: Messages sent to Maude monitor objects.

**SpanStart Message**:
```maude
spanStart($monitorOid, <span-term>)
```

**SpanEnd Message**:
```maude
spanEnd($monitorOid, <span-term>)
```

**Soup Term Format**:
Maude monitors maintain a "soup" (multiset) of terms representing system state:
```maude
<span-term> <event-term> <monitor-term> ... 
```

## Configuration Data Models

### LinoleumConfig
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/config/package.scala`
**Purpose**: Top-level configuration for Linoleum jobs.

**Fields**:
- `jobName: String` - Flink job name
- `localFlinkEnv: Boolean` - Local vs cluster execution
- `source: SourceConfig` - Kafka source configuration
- `sink: SinkConfig` - MongoDB sink configuration

### SourceConfig
**Purpose**: Kafka source configuration.

**Fields**:
- `kafkaBootstrapServers: String = "localhost:9092"`
- `kafkaTopics: String = "otlp_spans"`
- `kafkaGroupIdPrefix: String = "linolenum-cg"`
- `eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500)`

### SinkConfig
**Purpose**: Sink configuration with MongoDB and logging options.

**Fields**:
- `mongoDb: MongoDbConfig = MongoDbConfig()`
- `logMaudeTerms: Boolean = false`

**Constants**:
- `MaudeTermLogPath = "./maude_terms"` - Path for Maude term logging

### MongoDbConfig
**Purpose**: MongoDB connection and performance settings.

**Fields**:
- `mongoUri: String = "mongodb://localhost:27017"`
- `mongoDatabase: String = "linoleum"`
- `mongoCollection: String = "evaluatedSpans"`
- `mongoBatchSize: Int = 10`
- `mongoBatchIntervalMs: Long = 1000L`
- `mongoMaxRetries: Int = 3`

## Property Configuration Models

### LinoleumFormula.EvaluationConfig
**Purpose**: Configuration for LTLss formula evaluation.

**Fields**:
- `tickPeriod: Duration` - Size of tumbling windows for discretization
- `sessionGap: Duration` - Inactivity gap for session windows
- `allowedLateness: Duration = Duration.ofMillis(0)` - Late event tolerance

### MaudeMonitor.EvaluationConfig
**Purpose**: Configuration for Maude monitor evaluation.

**Fields**:
- `messageRewriteBound: Int = 100` - Maximum rewrite steps per message
- `sessionGap: Duration` - Inactivity gap for session windows
- `allowedLateness: Duration = Duration.ofMillis(0)` - Late event tolerance

### MaudeMonitor.StateConfig
**Purpose**: Flink state configuration for Maude monitors.

**Fields**:
- `ttl: Duration` - Time-to-live for keyed state
- `shouldIgnoreWindow: (String, List[LinoleumEvent]) => Boolean = (_, _) => false`
  - Function to ignore windows when state may have been cleaned by TTL

## Stream Processing Data Models

### SpanStreamEvaluatorParams[P]
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Parameters for span stream evaluation.

**Fields**:
- `property: P` - Property instance (LinoleumFormula or MaudeMonitor)
- `sessionGap: Duration` - Session window gap
- `allowedLateness: Duration` - Late event tolerance

### Letter and TimedLetter
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (formulas package object)
**Purpose**: Data structures for LTLss formula evaluation.

**Type Aliases**:
```scala
type Letter = List[LinoleumEvent]
type TimedLetter = (SscheckTime, Letter)
type SscheckFormula = Formula[Letter]
type SscheckFormulaSupplier = () => SscheckFormula
```

**Letter Operations**:
- `findMatchingSpan(matching: PartialFunction[LinoleumEvent, SpanInfo]): Option[SpanInfo]`
  - Find first span matching the partial function in a letter

## Maude Integration Data Models

### KeyByCriteria
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala` (maude package object)
**Purpose**: Strategy for grouping spans by key.

**Trait**:
```scala
trait KeyByCriteria {
  def keyBy(span: SpanInfo): String
}
```

**Implementations**:
1. `KeyByTraceId` (default): Groups by trace ID hexadecimal
2. `KeyByStringSpanAttribute(key: String)`: Groups by string span attribute value, falls back to trace ID

### MaudeMonitor
**Purpose**: Configuration for Maude-based property evaluation.

**Fields**:
- `name: String` - Human-readable property name
- `program: String` - Resource path to Maude program file
- `module: String` - Maude module name containing monitor class
- `monitorOid: String` - OID of monitor object in Maude soup
- `initialSoup: String` - Initial Maude soup term
- `property: String` - Maude property term to evaluate
- `keyBy: KeyByCriteria = KeyByTraceId` - Grouping strategy
- `dependencyPrograms: List[String] = List.empty` - Additional Maude programs
- `dependencyStdlibPrograms: List[String] = List.empty` - Maude stdlib dependencies
- `eqHooks: List[(String, () => MaudeHook)] = List.empty` - Equality hooks
- `rlHooks: List[(String, () => MaudeHook)] = List.empty` - Rewriting hooks
- `stateConfig: Option[MaudeMonitor.StateConfig] = None` - Flink state TTL config
- `config: MaudeMonitor.EvaluationConfig` - Evaluation parameters

## Time Utilities

### TimeUtils
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Time conversion utilities.

**Functions**:
- `nanosToMs(nanos: Long): Long` - Convert nanoseconds to milliseconds
- `msToNanos(ms: Long): Long` - Convert milliseconds to nanoseconds
- `instantToTimestamp(instant: Instant): Timestamp` - Convert Instant to protobuf Timestamp
- `timestampToInstant(timestamp: Timestamp): Instant` - Convert protobuf Timestamp to Instant

**Constants**:
- `million = pow(10, 6)` - Conversion factor

## Serialization Formats

### Protobuf Messages
**Source**: OpenTelemetry Protocol Buffers
- `ExportTraceServiceRequest`: Container for trace data from Kafka
- `Span`: OTEL span representation
- `Resource`: OTEL resource information
- `InstrumentationScope`: OTEL instrumentation scope

### BSON Documents
**Target**: MongoDB storage format
- Converted via `EvaluatedSpans.toBsonDocument()`
- Uses `org.bson.BsonDocument` for type-safe construction
- Time fields stored as `BsonDateTime` (milliseconds since epoch)

### Maude Terms
**Format**: Rewriting logic terms
- String escaping with `StringEscapeUtils.escapeJson()`
- Hexadecimal encoding for byte strings
- Structured object notation with attributes

## Data Flow Examples

### Span Processing Pipeline
1. **Input**: `ExportTraceServiceRequest` protobuf from Kafka
2. **Extraction**: Convert to `SpanInfo` objects
3. **Conversion**: Create `SpanStart` and `SpanEnd` events
4. **Grouping**: Apply `KeyByCriteria` to partition by key
5. **Windowing**: Session windows with configurable gap
6. **Evaluation**: Apply property evaluation (LTLss or Maude)
7. **Output**: `EvaluatedSpans` with truth values
8. **Storage**: Convert to BSON and write to MongoDB

### Maude Term Example
```maude
< span("1a164375b7463f1e8ddfe4a55e01cb5d/5f2c6b8a9d1e4f7a") : Span |
  traceId : "1a164375b7463f1e8ddfe4a55e01cb5d",
  spanId : "5f2c6b8a9d1e4f7a",
  parentSpanId : "3b8c9d2e4f5a6b7c",
  name : "http.request",
  startTimeUnixNano : 1640995200000000000,
  endTimeUnixNano : 1640995200015000000,
  attributes : ["http.method", "GET"] ["http.status_code", "200"],
  events : < event("1a164375b7463f1e8ddfe4a55e01cb5d/5f2c6b8a9d1e4f7a/0") : Event |
    timeUnixNano : 1640995200005000000,
    name : "server.recv",
    attributes : nil
  >
>
```

### BSON Document Example
```json
{
  "_id": ObjectId("65a1b2c3d4e5f67890123456"),
  "key": "1a164375b7463f1e8ddfe4a55e01cb5d",
  "startDate": ISODate("2024-01-01T00:00:00Z"),
  "evaluationDate": ISODate("2024-01-01T00:00:05.123Z"),
  "propertyName": "ResponseTimeUnder1s",
  "truthValue": "True"
}