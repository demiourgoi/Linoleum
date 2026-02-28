# Linoleum Interfaces

## Core Type Classes and Traits

### Property Type Class
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala`
**Purpose**: Abstract interface for property evaluation strategies.

```scala
trait Property[P] {
  /** Human-readable name of the property */
  def propertyName(property: P): String
  
  /** Parameters for Flink stream evaluation */
  def streamEvaluatorParams(property: P): SpanStreamEvaluatorParams[P]
  
  /** Evaluate the property on ordered events within a window */
  def evaluate(property: P)(
    key: String, 
    globalStateStore: KeyedStateStore, 
    orderedEvents: List[LinoleumEvent]
  ): TruthValue
  
  /** Extract grouping key from a span (default: trace ID) */
  def keyBy(property: P)(span: SpanInfo): String = span.hexTraceId
  
  /** Determine if a window should be ignored (default: false) */
  def shouldIgnoreWindow(
    property: P
  )(windowKey: String, orderedEvents: List[LinoleumEvent]): Boolean = false
}
```

### Property Syntax Extension
```scala
object PropertySyntax {
  implicit class PropertyOps[P](value: P) {
    def propertyName(implicit propertyInstance: Property[P]): String =
      propertyInstance.propertyName(value)
    
    def streamEvaluatorParams(implicit propertyInstance: Property[P]): SpanStreamEvaluatorParams[P] =
      propertyInstance.streamEvaluatorParams(value)
    
    def evaluate(
      key: String, 
      globalStateStore: KeyedStateStore, 
      orderedEvents: List[LinoleumEvent]
    )(implicit propertyInstance: Property[P]): TruthValue =
      propertyInstance.evaluate(value)(key, globalStateStore, orderedEvents)
    
    def keyBy(span: SpanInfo)(implicit propertyInstance: Property[P]): String =
      propertyInstance.keyBy(value)(span)
    
    def shouldIgnoreWindow(
      windowKey: String, 
      orderedEvents: List[LinoleumEvent]
    )(implicit propertyInstance: Property[P]): Boolean =
      propertyInstance.shouldIgnoreWindow(value)(windowKey, orderedEvents)
  }
}
```

### Property Instances
**Location**: `PropertyInstances` companion object in `SpanStreamEvaluator.scala`

```scala
object PropertyInstances extends Serializable {
  // LTLss formula property instance
  implicit val formulaProperty: Property[LinoleumFormula] = ...
  
  // Maude monitor property instance  
  implicit val maudeMonitorProperty: Property[MaudeMonitor] = ...
}
```

## Data Model Interfaces

### LinoleumEvent Hierarchy
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/messages/package.scala`

```scala
sealed trait LinoleumEvent {
  /** UNIX epoch time in nanoseconds positioning this event */
  def epochUnixNano: Long
  
  /** The span associated with this event */
  def span: SpanInfo
  
  /** Compact string representation for logging */
  def shortToString: String
  
  /** Maude representation targeting the specified OID */
  def toMaude(oid: String): String
}

case class SpanStart(span: SpanInfo) extends LinoleumEvent {
  override def epochUnixNano: Long = span.getSpan.getStartTimeUnixNano
  override def toMaude(oid: String): String = s"""spanStart($oid, ${span.toMaude})"""
}

case class SpanEnd(span: SpanInfo) extends LinoleumEvent {
  override def epochUnixNano: Long = span.getSpan.getEndTimeUnixNano
  override def toMaude(oid: String): String = s"""spanEnd($oid, ${span.toMaude})"""
}
```

### SpanInfo Extension Methods
```scala
implicit class LinoleumSpanInfo(self: SpanInfo) {
  /** Hexadecimal representation of trace ID */
  val hexTraceId: String = byteString2HexString(self.getSpan.getTraceId)
  
  /** Hexadecimal representation of span ID */
  val hexSpanId: String = byteString2HexString(self.getSpan.getSpanId)
  
  /** Check if this is a root span (no parent) */
  val isRoot: Boolean = self.getSpan.getParentSpanId.isEmpty
  
  /** Original span ID as UTF-8 string */
  val spanId: String = self.getSpan().getSpanId().toStringUtf8()
  
  /** Check if span has given name */
  def isNamed(name: String): Boolean = self.getSpan.getName == name
  
  /** Compact string representation */
  def shortToString: String = s"(${self.getSpan.getName}, $hexSpanId, $hexTraceId)"
  
  /** Convert to Maude term representation */
  def toMaude: String = {
    val span = self.getSpan()
    spanToMaude(span)
  }
}
```

## Configuration Interfaces

### LinoleumConfig
**Location**: `lib/src/main/scala/io/github/demiourgoi/linoleum/config/package.scala`

```scala
case class LinoleumConfig(
  jobName: String,
  localFlinkEnv: Boolean,
  source: SourceConfig = SourceConfig(),
  sink: SinkConfig = SinkConfig()
)
```

### SourceConfig
```scala
case class SourceConfig(
  kafkaBootstrapServers: String = "localhost:9092",
  kafkaTopics: String = "otlp_spans",
  kafkaGroupIdPrefix: String = "linolenum-cg",
  eventsMaxOutOfOrderness: Duration = Duration.ofMillis(500)
)
```

### SinkConfig
```scala
case class SinkConfig(
  mongoDb: MongoDbConfig = MongoDbConfig(),
  logMaudeTerms: Boolean = false
)

object SinkConfig {
  val MaudeTermLogPath = "./maude_terms"
}
```

### MongoDbConfig
```scala
case class MongoDbConfig(
  mongoUri: String = "mongodb://localhost:27017",
  mongoDatabase: String = "linoleum",
  mongoCollection: String = "evaluatedSpans",
  mongoBatchSize: Int = 10,
  mongoBatchIntervalMs: Long = 1000L,
  mongoMaxRetries: Int = 3
)
```

## Property Configuration Interfaces

### LinoleumFormula Configuration
```scala
object LinoleumFormula {
  case class EvaluationConfig(
    tickPeriod: Duration,
    sessionGap: Duration,
    allowedLateness: Duration = Duration.ofMillis(0)
  )
}

case class LinoleumFormula(
  name: String,
  config: LinoleumFormula.EvaluationConfig,
  formula: SscheckFormulaSupplier  // () => Formula[Letter]
)
```

### MaudeMonitor Configuration
```scala
object MaudeMonitor {
  case class EvaluationConfig(
    messageRewriteBound: Int = 100,
    sessionGap: Duration,
    allowedLateness: Duration = Duration.ofMillis(0)
  )
  
  case class StateConfig(
    ttl: Duration,
    shouldIgnoreWindow: (String, List[LinoleumEvent]) => Boolean = (_, _) => false
  )
}

case class MaudeMonitor(
  name: String,
  program: String,                    // Resource path, e.g., "maude/lotrbot_imagegen_safety.maude"
  module: String,                     // Maude module name
  monitorOid: String,                 // Maude term for monitor object OID
  initialSoup: String,               // Maude term for initial soup
  property: String,                   // Maude term for property to evaluate
  keyBy: KeyByCriteria = KeyByTraceId,
  dependencyPrograms: List[String] = List.empty,
  dependencyStdlibPrograms: List[String] = List.empty,
  eqHooks: List[(String, () => MaudeHook)] = List.empty,
  rlHooks: List[(String, () => MaudeHook)] = List.empty,
  stateConfig: Option[MaudeMonitor.StateConfig] = None,
  config: MaudeMonitor.EvaluationConfig
)
```

### KeyByCriteria Trait
```scala
trait KeyByCriteria {
  def keyBy(span: SpanInfo): String
}

case class KeyByStringSpanAttribute(key: String) extends KeyByCriteria {
  override def keyBy(span: SpanInfo): String = {
    val agentNameOpt = span
      .getSpan()
      .getAttributesList()
      .asScala
      .toList
      .collectFirst {
        case kv if (kv.getKey() == key) && (kv.getValue().hasStringValue()) =>
          kv.getValue().getStringValue()
      }
    agentNameOpt.getOrElse(KeyByTraceId.keyBy(span))
  }
}

case object KeyByTraceId extends KeyByCriteria {
  override def keyBy(span: SpanInfo): String = span.hexTraceId
}
```

## Evaluation Result Interface

### TruthValue ADT
```scala
sealed trait TruthValue
@SerialVersionUID(1L)
case object True extends TruthValue with Serializable
@SerialVersionUID(1L)
case object False extends TruthValue with Serializable
@SerialVersionUID(1L)
case object Undecided extends TruthValue with Serializable
```

### EvaluatedSpans Result
```scala
@SerialVersionUID(1L)
case class EvaluatedSpans(
  key: String,                    // Grouping key (trace ID or custom)
  startTimeUnixNano: Long,        // Window start time
  propertyName: String,           // Property that was evaluated
  truthValue: TruthValue          // Evaluation result
) {
  /** Convert to MongoDB BSON document */
  def toBsonDocument: BsonDocument = {
    val now = Instant.now()
    new BsonDocument()
      .append("key", new BsonString(key))
      .append("startDate", new BsonDateTime(nanosToMs(startTimeUnixNano)))
      .append("evaluationDate", new BsonDateTime(now.toEpochMilli()))
      .append("propertyName", new BsonString(propertyName))
      .append("truthValue", new BsonString(truthValue.toString()))
  }
}
```

## Stream Processing Interfaces

### SpanStreamEvaluatorParams
```scala
case class SpanStreamEvaluatorParams[P](
  property: P,
  sessionGap: Duration,
  allowedLateness: Duration
)(implicit propInstance: Property[P])
```

### ProcessWindow Function
```scala
private[evaluator] class ProcessWindow[W <: TimeWindow, P](
  private val params: SpanStreamEvaluatorParams[P]
)(implicit propInstance: Property[P])
  extends ProcessWindowFunction[SpanInfo, EvaluatedSpans, String, W] {
  
  override def process(
    key: String,
    context: ProcessWindowsCtx[W],
    spanInfos: jlang.Iterable[SpanInfo],
    out: Collector[EvaluatedSpans]
  ): Unit = {
    // Convert spans to events, evaluate property, emit results
  }
}
```

## Maude Integration Interfaces

### MaudeModules Object
```scala
object MaudeModules {
  private lazy val maudeRuntime: MaudeRuntime = ...
  
  def loadModule(maudeProgramResourcePath: String, moduleName: String): MaudeModule = ...
  
  lazy val traceTypesModule: MaudeModule =
    loadModule("maude/linoleum/trace.maude", "TRACE-CLASS-OBJECTS")
  
  lazy val satisfactionModule: MaudeModule =
    loadStdLibModule("model-checker.maude", "SATISFACTION")
  
  lazy val jsonModule: MaudeModule = ...
  
  def loadProgram(maudeProgramResourcePath: String): Unit = ...
  
  def loadStdLibModule(maudeProgramFileName: String, moduleName: String): MaudeModule = ...
  
  def loadStdLibProgram(maudeProgramFileName: String): Unit = ...
  
  def connectEqHook[H <: MaudeHook](operatorName: String, hookThunk: => H): H = ...
  
  def connectRlHook[H <: MaudeHook](operatorName: String, hookThunk: => H): H = ...
  
  def runWithLock[A](body: => A): A = ...
}
```

## Source and Sink Interfaces

### LinoleumSrc Class
```scala
class LinoleumSrc(@transient val cfg: LinoleumConfig)
  extends FlatMapFunction[ExportTraceServiceRequest, SpanInfo]
  with SerializableTimestampAssigner[SpanInfo]
  with Serializable {
  
  def apply(env: StreamExecutionEnvironment): SpanInfoStream = ...
  
  override def flatMap(
    exportTracesRequest: ExportTraceServiceRequest,
    out: Collector[SpanInfo]
  ): Unit = ...
  
  override def extractTimestamp(spanInfo: SpanInfo, recordTimestamp: Long): Long =
    nanosToMs(spanInfo.getSpan.getStartTimeUnixNano)
}
```

### LinoleumSink Class
```scala
class LinoleumSink(cfg: LinoleumConfig) {
  def apply(evaluatedSpans: DataStream[EvaluatedSpans]): Unit = ...
  
  private def buildSink(): MongoSink[EvaluatedSpans] = ...
}
```

## Utility Interfaces

### TimeUtils Object
```scala
object TimeUtils {
  val million = pow(10, 6)
  
  def nanosToMs(nanos: Long): Long = (nanos / million).longValue
  
  def msToNanos(ms: Long): Long = ms * million.longValue
  
  def instantToTimestamp(instant: Instant): Timestamp = ...
  
  def timestampToInstant(timestamp: Timestamp): Instant = ...
}
```

### Maude Conversion Functions
```scala
package object maude {
  import utils.byteString2HexString
  
  /** Convert OTEL span to Maude term representation */
  def spanToMaude(span: Span): String = ...
  
  private def spanAttributesToMaude(attributes: jutil.List[KeyValue]): String = ...
  
  private def keyValueToMaude(kv: KeyValue): String = ...
  
  private def anyValueToMaude(anyValue: AnyValue): String = ...
  
  /** Escape JSON strings for Maude compatibility */
  private def stringValueToMaude(stringValue: String): String = ...
}
```

## Type Aliases

```scala
package object source {
  type SpanInfoStream = DataStream[SpanInfo]
}

package object evaluator {
  type VerifiedSpansStream = DataStream[EvaluatedSpans]
}

package object formulas {
  type Letter = List[LinoleumEvent]
  type TimedLetter = (SscheckTime, Letter)
  type SscheckFormula = Formula[Letter]
  type SscheckFormulaSupplier = () => SscheckFormula
}
```

## Factory Methods

### Linoleum Object Entry Points
```scala
object Linoleum {
  def execute[P](cfg: LinoleumConfig)(property: P)(implicit
    propInstance: Property[P]
  ): Unit = ...
  
  def execute(cfg: LinoleumConfig, formula: LinoleumFormula): Unit = ...
  
  def execute(cfg: LinoleumConfig, mon: MaudeMonitor): Unit = ...
  
  private[linoleum] def setupFlinkJob[P](
    linolenumCfg: LinoleumConfig,
    property: P
  )(implicit propInstance: Property[P]): StreamExecutionEnvironment = ...
}
```

### Formula Creation Helper
```scala
def linoleumFormula(formula: SscheckFormula): SscheckFormulaSupplier =
  new SscheckFormulaSupplier {
    def apply(): SscheckFormula = formula
  }
```

## Serialization Interfaces

### ExportTraceServiceRequestProtoDeserializer
```scala
class ExportTraceServiceRequestProtoDeserializer
  extends DeserializationSchema[ExportTraceServiceRequest]
  with Serializable {
  
  override def deserialize(bytes: Array[Byte]): ExportTraceServiceRequest = ...
  
  override def isEndOfStream(t: ExportTraceServiceRequest): Boolean = false
  
  override def getProducedType: TypeInformation[ExportTraceServiceRequest] =
    TypeInformation.of(classOf[ExportTraceServiceRequest])
}
```

## Error Types

```scala
object EventCollectionMultipleRootSpansError {
  def message(rootSpanInfo: SpanInfo, spanInfo: SpanInfo) =
    s"Multiple root spans found for trace with id ${rootSpanInfo.hexTraceId}: $rootSpanInfo, $spanInfo"
}

class EventCollectionMultipleRootSpansError(
  rootSpanInfo: SpanInfo,
  spanInfo: SpanInfo
) extends EventCollectionError(
  EventCollectionMultipleRootSpansError.message(rootSpanInfo, spanInfo)
)

class EventCollectionError(message: String) extends Exception(message)