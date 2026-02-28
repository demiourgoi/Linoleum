# Linoleum Workflows

## Overview

Linoleum processes OpenTelemetry spans through a series of well-defined workflows to evaluate temporal properties. The system supports two types of property evaluation: LTLss formulas (stateless) and Maude programs (stateful).

## End-to-End Processing Workflow

```mermaid
flowchart TD
    A[Kafka Topic<br/>OTEL Spans] --> B[LinoleumSrc<br/>Kafka Source]
    B --> C[SpanInfo Stream]
    C --> D[Key By<br/>Trace ID or Attribute]
    D --> E[Session Windows<br/>with Gap]
    E --> F{Property Type?}
    F -->|LTLss| G[LinoleumFormula<br/>Stateless Evaluation]
    F -->|Maude| H[MaudeMonitor<br/>Stateful Evaluation]
    G --> I[Truth Value<br/>True/False/Undecided]
    H --> I
    I --> J[LinoleumSink<br/>MongoDB Storage]
    J --> K[MongoDB Collection<br/>evaluatedSpans]
```

## Span Ingestion Workflow

### 1. Kafka Source Processing
```mermaid
sequenceDiagram
    participant K as Kafka
    participant LS as LinoleumSrc
    participant F as Flink Stream
    
    K->>LS: ExportTraceServiceRequest protobuf
    LS->>LS: Deserialize protobuf
    LS->>LS: Extract SpanInfo objects
    LS->>LS: Extract event timestamps
    LS->>LS: Generate watermarks
    LS->>F: SpanInfo Stream with timestamps
```

**Steps**:
1. **Deserialization**: Convert protobuf `ExportTraceServiceRequest` to `SpanInfo` objects
2. **Timestamp Extraction**: Use span start time as event time
3. **Watermark Generation**: Configurable out-of-orderness tolerance
4. **Stream Creation**: Produce `SpanInfoStream` for downstream processing

**Key Configuration**:
- `kafkaBootstrapServers`: Kafka broker addresses
- `kafkaTopics`: Source topics (default: `otlp_spans`)
- `eventsMaxOutOfOrderness`: Watermark generation parameter

## Event Processing Workflow

### 2. Span to Event Conversion
```mermaid
flowchart LR
    A[SpanInfo] --> B[SpanStart Event]
    A --> C[SpanEnd Event]
    B --> D[Event Stream]
    C --> D
```

**Conversion Logic**:
- Each `SpanInfo` generates two `LinoleumEvent`s:
  - `SpanStart`: Timestamp = span start time
  - `SpanEnd`: Timestamp = span end time
- Events are ordered by their `epochUnixNano` timestamp
- Duplicate spans (same span ID) are filtered out

### 3. Key Partitioning and Windowing
```mermaid
flowchart TD
    A[Event Stream] --> B[Key By<br/>Trace ID or Attribute]
    B --> C[Session Windows<br/>Configurable Gap]
    C --> D[Window Trigger<br/>on Session Gap]
    D --> E[Process Window Function]
```

**Key Partitioning**:
- Default: Group by trace ID (`KeyByTraceId`)
- Alternative: Group by span attribute value (`KeyByStringSpanAttribute`)
- Custom: Implement `KeyByCriteria` trait for custom grouping

**Session Windows**:
- Windows close after configurable inactivity gap
- Configurable allowed lateness for late events
- Each window contains events for a single trace (or custom key)

## Property Evaluation Workflows

### 4. LTLss Formula Evaluation
```mermaid
flowchart TD
    A[Window Events] --> B[Order by Epoch]
    B --> C[Find Root Span]
    C --> D[Discard Events<br/>Before Root Start]
    D --> E[Create Tumbling Windows<br/>Tick Period]
    E --> F[Convert to Letters<br/>List of Events]
    F --> G[Apply LTLss Formula]
    G --> H[Final Formula State]
    H --> I[Map to Truth Value]
```

**LTLss-Specific Logic**:
- Only processes first window containing root span
- Events before root span start are discarded
- Time discretized into tumbling windows of `tickPeriod` duration
- Formula evaluated over sequence of letters (event lists)

**Evaluation Process**:
1. **Letter Construction**: Group events into time-based letters
2. **Formula Application**: Apply LTLss formula to letter sequence
3. **State Tracking**: Maintain formula state across letters
4. **Result Mapping**: Convert final formula state to `TruthValue`

### 5. Maude Monitor Evaluation
```mermaid
flowchart TD
    A[Window Events] --> B[Load Maude Modules]
    B --> C[Initialize/Restore Soup<br/>from Flink State]
    C --> D{Process Each Event}
    D --> E[Create Maude Message]
    E --> F[Add to Soup<br/>and Rewrite]
    F --> G[Update Soup State]
    G --> D
    D --> H[All Events Processed]
    H --> I[Evaluate Soup<br/>Against Property]
    I --> J[Save Soup State<br/>to Flink]
    J --> K[Truth Value]
```

**Maude-Specific Logic**:
- State persists across windows via Flink keyed state
- Configurable TTL for state cleanup
- Custom window filtering for TTL edge cases
- Supports equality and rewriting hooks

**Evaluation Process**:
1. **Module Loading**: Load Maude program and dependencies
2. **State Management**: Initialize or restore soup from Flink state
3. **Event Processing**: Convert each event to Maude message, add to soup, rewrite
4. **Property Evaluation**: Evaluate final soup against property using `|=` operator
5. **State Persistence**: Save updated soup to Flink state

## Result Storage Workflow

### 6. MongoDB Sink Processing
```mermaid
sequenceDiagram
    participant F as Flink Stream
    participant LS as LinoleumSink
    participant M as MongoDB
    
    F->>LS: EvaluatedSpans objects
    LS->>LS: Convert to BSON documents
    LS->>LS: Batch documents
    LS->>M: Write batch with retries
    M-->>LS: Write acknowledgment
```

**Storage Process**:
1. **Conversion**: `EvaluatedSpans` → BSON documents
2. **Batching**: Configurable batch size and interval
3. **Writing**: At-least-once delivery to MongoDB
4. **Retry Logic**: Configurable retry attempts

**Document Structure**:
```json
{
  "key": "trace-id-or-custom-key",
  "startDate": ISODate("2024-01-01T00:00:00Z"),
  "evaluationDate": ISODate("2024-01-01T00:00:05.123Z"),
  "propertyName": "PropertyName",
  "truthValue": "True|False|Undecided"
}
```

## Configuration and Execution Workflow

### 7. Job Setup and Execution
```mermaid
flowchart TD
    A[LinoleumConfig] --> B[Create Flink Environment]
    B --> C[Configure Serialization]
    C --> D[Create LinoleumSrc]
    D --> E[Create SpanStreamEvaluator]
    E --> F[Configure Property]
    F --> G[Create LinoleumSink]
    G --> H[Execute Flink Job]
```

**Job Configuration**:
- **Local vs Cluster**: `localFlinkEnv` flag controls execution mode
- **Serialization**: Kryo with protobuf support for OTEL messages
- **Web UI**: Local mode provides Flink web UI at `localhost:8081`

**Execution Steps**:
1. **Environment Setup**: Configure Flink execution environment
2. **Source Creation**: Set up Kafka source with watermark strategy
3. **Processing Pipeline**: Apply keyBy, windows, and evaluation
4. **Sink Configuration**: Set up MongoDB sink with batching
5. **Job Execution**: Execute Flink job with specified name

## Error Handling and Recovery Workflows

### 8. Error Recovery Strategies
```mermaid
flowchart TD
    A[Processing Error] --> B{Error Type}
    B -->|Duplicate Span| C[Skip Duplicate<br/>Log Warning]
    B -->|Multiple Roots| D[EventCollectionError<br/>Side Output]
    B -->|Maude Parse Error| E[Log Error<br/>Skip Event]
    B -->|State Corruption| F[TTL Cleanup<br/>Window Filtering]
    B -->|Network Issue| G[Retry Logic<br/>Exponential Backoff]
```

**Error Types and Handling**:
- **Duplicate Spans**: Skip with warning log
- **Multiple Root Spans**: `EventCollectionMultipleRootSpansError` to side output
- **Maude Runtime Errors**: Log errors, skip problematic events
- **State TTL Issues**: Custom `shouldIgnoreWindow` function
- **Network Failures**: MongoDB sink retry with exponential backoff

### 9. State Management Workflow
```mermaid
flowchart TD
    A[Window Processing] --> B{State Exists?}
    B -->|Yes| C[Restore from<br/>Flink State]
    B -->|No| D[Initialize from<br/>MaudeMonitor config]
    C --> E[Process Events]
    D --> E
    E --> F[Update Soup]
    F --> G[Save to Flink State<br/>with TTL]
    G --> H{TTL Expired?}
    H -->|Yes| I[State Cleanup]
    H -->|No| J[State Persisted]
```

**State Lifecycle**:
1. **Initialization**: First window for a key initializes state from `initialSoup`
2. **Restoration**: Subsequent windows restore state from Flink keyed state
3. **Update**: Events update soup state through Maude rewriting
4. **Persistence**: Updated state saved back to Flink
5. **Cleanup**: TTL-based automatic state expiration

## Monitoring and Debugging Workflows

### 10. Debug Logging Workflow
```mermaid
flowchart TD
    A[Enable Debug Flag] --> B[Log Maude Terms]
    B --> C[Write to File<br/>./maude_terms]
    C --> D[Format: One Term per Line]
    D --> E[Use for<br/>Offline Analysis]
```

**Debug Features**:
- `logMaudeTerms`: Write all processed spans as Maude terms to file
- **Log Levels**: Configurable logging for different components
- **Metrics**: Flink metrics integration for monitoring
- **Side Outputs**: Error streams for problematic traces

### 11. Performance Monitoring
```mermaid
flowchart TD
    A[Flink Metrics] --> B[Throughput<br/>Events/Second]
    B --> C[Latency<br/>Processing Time]
    C --> D[State Size<br/>Memory Usage]
    D --> E[Window Statistics<br/>Count/Duration]
    E --> F[Aggregate Metrics<br/>Dashboard]
```

**Monitoring Points**:
- **Source Metrics**: Kafka consumption rates
- **Processing Metrics**: Event processing throughput
- **State Metrics**: Maude soup size and growth
- **Sink Metrics**: MongoDB write performance
- **Window Metrics**: Session window statistics

## Extension Workflows

### 12. Adding New Property Types
```mermaid
flowchart TD
    A[Define Property Type] --> B[Implement Property[P] trait]
    B --> C[Add to PropertyInstances]
    C --> D[Create Factory Methods]
    D --> E[Update Configuration]
    E --> F[Test Integration]
    F --> G[Deploy]
```

**Extension Steps**:
1. **Type Definition**: Define property configuration class
2. **Trait Implementation**: Implement `Property[P]` type class
3. **Instance Registration**: Add to `PropertyInstances` companion object
4. **Factory Methods**: Add to `Linoleum` object for easy usage
5. **Configuration**: Update config classes if needed
6. **Testing**: Unit and integration tests
7. **Deployment**: Package and deploy

### 13. Custom Key Grouping
```scala
case class CustomKeyBy(attribute: String) extends KeyByCriteria {
  override def keyBy(span: SpanInfo): String = {
    span.getSpan.getAttributesList.asScala
      .find(_.getKey == attribute)
      .map(kv => anyValueToMaude(kv.getValue))
      .getOrElse(span.hexTraceId)
  }
}
```

**Customization Points**:
- **KeyByCriteria**: Implement custom grouping logic
- **Maude Hooks**: Add equality/rewriting hooks for Maude integration
- **Window Filters**: Custom logic for ignoring windows
- **Serialization**: Custom serializers for new data types

## Example Usage Workflows

### 14. LTLss Formula Example
```scala
val formula = LinoleumFormula(
  name = "ResponseTimeUnder1s",
  config = LinoleumFormula.EvaluationConfig(
    tickPeriod = Duration.ofSeconds(1),
    sessionGap = Duration.ofMinutes(5),
    allowedLateness = Duration.ofSeconds(30)
  ),
  formula = linoleumFormula {
    always { letter: Letter =>
      letter.findMatchingSpan {
        case SpanStart(span) if span.isNamed("http.request") => span
      }.forall { span =>
        val duration = span.getSpan.getEndTimeUnixNano - 
                      span.getSpan.getStartTimeUnixNano
        duration <= 1_000_000_000L  // 1 second in nanoseconds
      }
    } during 60  // Evaluate for 60 time units
  }
)

val config = LinoleumConfig(
  jobName = "response-time-monitor",
  localFlinkEnv = true,
  source = SourceConfig(kafkaTopics = "http-traces"),
  sink = SinkConfig()
)

Linoleum.execute(config, formula)
```

### 15. Maude Monitor Example
```scala
val monitor = MaudeMonitor(
  name = "ImageGenSafety",
  program = "maude/lotrbot_imagegen_safety.maude",
  module = "IMAGE-GEN-SAFETY",
  monitorOid = "'monitor",
  initialSoup = "< 'monitor : Monitor | state: idle >",
  property = "safe",
  keyBy = KeyByStringSpanAttribute("agent.name"),
  dependencyPrograms = List("maude/linoleum/trace.maude"),
  dependencyStdlibPrograms = List("model-checker.maude"),
  stateConfig = Some(MaudeMonitor.StateConfig(
    ttl = Duration.ofHours(24),
    shouldIgnoreWindow = (key, events) => 
      events.isEmpty || events.head.span.getSpan.getName.contains("test")
  )),
  config = MaudeMonitor.EvaluationConfig(
    messageRewriteBound = 100,
    sessionGap = Duration.ofMinutes(10),
    allowedLateness = Duration.ofSeconds(60)
  )
)

val config = LinoleumConfig(
  jobName = "image-gen-safety",
  localFlinkEnv = false,
  source = SourceConfig(kafkaTopics = "image-gen-traces"),
  sink = SinkConfig(logMaudeTerms = true)
)

Linoleum.execute(config, monitor)