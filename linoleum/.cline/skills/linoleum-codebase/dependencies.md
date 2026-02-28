# Linoleum Dependencies

## Build Dependencies

### Gradle Configuration
**Location**: `lib/build.gradle`, `gradle/libs.versions.toml`
**Build System**: Gradle with Kotlin DSL

**Key Dependencies**:
```kotlin
dependencies {
    // Scala and Flink
    implementation("org.scala-lang:scala-library:2.13.12")
    implementation("org.apache.flink:flink-streaming-scala_2.13:1.20.1")
    implementation("org.apache.flink:flink-clients:1.20.1")
    
    // Kafka Connector
    implementation("org.apache.flink:flink-connector-kafka:3.2.0-1.20")
    
    // MongoDB Connector
    implementation("org.mongodb.scala:mongo-scala-bson_2.13:4.11.1")
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")
    
    // OpenTelemetry Protocol Buffers
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.0.0-alpha")
    
    // Jaeger Tracing
    implementation("io.jaegertracing:jaeger-api:1.8.1")
    
    // Maude Integration
    implementation("io.github.demiourgoi:maude-java-bindings:0.1.0")
    
    // LTLss Library
    implementation("io.github.demiourgoi:sscheck-core:0.1.0")
    
    // Utilities
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Testing
    testImplementation("org.scalatest:scalatest_2.13:3.2.17")
    testImplementation("org.apache.flink:flink-test-utils:1.20.1")
    testImplementation("org.testcontainers:mongodb:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
}
```

## Runtime Dependencies

### Apache Flink
**Version**: 1.20.1
**Purpose**: Stream processing framework

**Key Components**:
- `flink-streaming-scala`: Scala API for stream processing
- `flink-clients`: Client utilities
- `flink-connector-kafka`: Kafka source integration
- `flink-statebackend-rocksdb`: State backend for large state
- `flink-checkpointing`: Fault tolerance via checkpoints

**Configuration**:
- **State Backend**: RocksDB for large state volumes
- **Checkpointing**: Enabled with configurable interval
- **Watermarks**: Event-time processing with configurable out-of-orderness
- **Parallelism**: Configurable operator parallelism

### MongoDB
**Version**: 4.11.1 (driver)
**Purpose**: Result storage and querying

**Components**:
- `mongodb-driver-sync`: Synchronous MongoDB driver
- `mongo-scala-bson`: BSON support for Scala

**Configuration**:
- **Connection URI**: `mongodb://localhost:27017` (default)
- **Database**: `linoleum` (default)
- **Collection**: `evaluatedSpans` (default)
- **Batch Size**: 10 documents (configurable)
- **Batch Interval**: 1000ms (configurable)
- **Retry Logic**: Exponential backoff with max retries

### Apache Kafka
**Version**: 3.2.0 (Flink connector)
**Purpose**: Source for OpenTelemetry spans

**Configuration**:
- **Bootstrap Servers**: `localhost:9092` (default)
- **Topics**: `otlp_spans` (default)
- **Consumer Group**: `linolenum-cg-{random-uuid}`
- **Deserializer**: Protobuf deserializer for `ExportTraceServiceRequest`

### OpenTelemetry Protocol
**Version**: 1.0.0-alpha
**Purpose**: Span data format

**Components**:
- `opentelemetry-proto`: Protocol buffer definitions
- Generated Java classes for span, resource, instrumentation scope

**Data Model**:
- `Span`: Trace span with attributes, events, links
- `Resource`: Service/resource information
- `InstrumentationScope`: Instrumentation library details
- `ExportTraceServiceRequest`: Batch container for spans

### Maude Java Bindings
**Version**: 0.1.0
**Purpose**: Integration with Maude rewriting logic system

**Features**:
- Thread-safe Maude runtime access
- Module loading from resources
- Term parsing and rewriting
- Equality and rewriting hooks
- Soup term management

**Usage**:
- Load Maude programs from classpath resources
- Execute rewrites with configurable bounds
- Maintain soup state across Flink windows
- Evaluate properties using `|=` operator

### sscheck-core
**Version**: 0.1.0
**Purpose**: LTLss (Linear Temporal Logic for Streams and Systems) evaluation

**Features**:
- Temporal formula evaluation over event streams
- Time discretization with tumbling windows
- Formula state tracking
- Truth value mapping (True/False/Undecided)

**Integration**:
- `Formula[Letter]` type for LTLss formulas
- `SscheckFormulaSupplier` for formula creation
- Time-based letter construction from events

## Development Dependencies

### Testing
**ScalaTest**: Unit and integration testing
**Flink Test Utils**: Flink streaming test utilities
**TestContainers**: MongoDB and Kafka for integration testing
**Mockito**: Mocking framework for unit tests

### Build Tools
**Scala 2.13**: Primary programming language
**sbt**: Alternative build tool (optional)
**Protobuf Compiler**: Protocol buffer code generation
**ScalaFmt**: Code formatting

## Configuration Dependencies

### Application Configuration
**Typesafe Config**: For external configuration (if used)
**Environment Variables**: For sensitive data (Kafka/MongoDB credentials)
**System Properties**: For Flink configuration

### Serialization
**Kryo**: Custom serializers for Flink
**Protocol Buffers**: Efficient binary serialization
**BSON**: MongoDB document format

## External Services

### Required Services
1. **Kafka Cluster**: For span ingestion
   - Default: `localhost:9092`
   - Topics: `otlp_spans` (or configurable)

2. **MongoDB Instance**: For result storage
   - Default: `mongodb://localhost:27017`
   - Database: `linoleum`
   - Collection: `evaluatedSpans`

3. **Maude Runtime**: For MaudeMonitor evaluation
   - Embedded via Java bindings
   - No external service required

4. **Flink Cluster** (optional): For distributed execution
   - JobManager: `localhost:8081` (local mode)
   - TaskManagers: Configurable parallelism

### Optional Services
1. **Prometheus/Grafana**: For monitoring Flink metrics
2. **Elasticsearch/Kibana**: For log aggregation and analysis
3. **Jaeger/Zipkin**: For distributed tracing visualization

## Version Compatibility

### Scala Compatibility
- **Scala**: 2.13.x
- **Java**: 11+ (for Flink 1.20)
- **sbt**: 1.8+ (if using sbt build)

### Flink Compatibility
- **Flink**: 1.20.x
- **Kafka Connector**: 3.2.0-1.20 (for Flink 1.20)
- **State Backend**: RocksDB recommended for production

### MongoDB Compatibility
- **MongoDB Server**: 4.4+ (driver supports 3.6+)
- **BSON**: Version 4.x format
- **Scala Driver**: 4.11.x

### Protocol Buffer Compatibility
- **Protobuf**: Version 3 syntax
- **Java Generated Code**: Compatible with protobuf-java 3.25.x
- **OpenTelemetry**: OTEL proto 1.0.0-alpha

## Dependency Management

### Version Control
**Location**: `gradle/libs.versions.toml`
**Purpose**: Centralized version management

**Example**:
```toml
[versions]
scala = "2.13.12"
flink = "1.20.1"
mongodb = "4.11.1"
kafka = "3.2.0"
opentelemetry-proto = "1.0.0-alpha"

[libraries]
scala-library = { module = "org.scala-lang:scala-library", version.ref = "scala" }
flink-streaming-scala = { module = "org.apache.flink:flink-streaming-scala_2.13", version.ref = "flink" }
flink-connector-kafka = { module = "org.apache.flink:flink-connector-kafka", version.ref = "kafka" }
mongodb-driver-sync = { module = "org.mongodb:mongodb-driver-sync", version.ref = "mongodb" }
opentelemetry-proto = { module = "io.opentelemetry.proto:opentelemetry-proto", version.ref = "opentelemetry-proto" }
```

### Transitive Dependencies
**Key Transitive Dependencies**:
- `netty`, `logback`, `slf4j`: Networking and logging
- `guava`, `commons-*`: Utilities
- `jackson`: JSON serialization
- `rocksdbjni`: RocksDB native bindings

**Conflict Resolution**:
- Gradle dependency resolution
- Force versions for known conflicts
- Exclude problematic transitive dependencies

## Build and Deployment

### Development Environment
**Requirements**:
- Java 11+
- Scala 2.13.x
- Gradle 7.6+
- Docker (for test containers)
- Kafka (local or remote)
- MongoDB (local or remote)

**Build Commands**:
```bash
# Build project
./gradlew :lib:build

# Run tests
./gradlew :lib:test

# Run specific test
./gradlew :lib:test --tests "io.github.demiourgoi.linoleum.evaluator.SpanStreamEvaluatorTest"

# Create jar
./gradlew :lib:jar

# Run with local Flink
./gradlew :lib:run --args="--local"
```

### Production Deployment
**Packaging**:
- Fat/uber jar with all dependencies
- Flink application jar format
- Docker image with Flink and dependencies

**Configuration**:
- External configuration files
- Environment variables for secrets
- Flink configuration via `flink-conf.yaml`

**Monitoring**:
- Flink metrics to Prometheus
- Application logs to ELK stack
- Custom metrics for Maude evaluation statistics

## Known Issues and Workarounds

### Maude Runtime
**Issue**: Maude Java bindings not thread-safe
**Workaround**: Synchronized access via `MaudeModules.runWithLock()`

**Issue**: Maude program loading from resources
**Workaround**: Ensure Maude files in `src/main/resources/maude/`

### Flink Serialization
**Issue**: Protobuf serialization with Kryo
**Workaround**: Register custom serializers in Flink configuration

**Issue**: State serialization for Maude terms
**Workaround**: String representation with custom serializers

### MongoDB Connectivity
**Issue**: Connection pool exhaustion
**Workaround**: Configure connection pool size in `MongoDbConfig`

**Issue**: Network timeouts
**Workaround**: Retry logic with exponential backoff

### Kafka Deserialization
**Issue**: Protobuf deserialization performance
**Workaround**: Batch processing and efficient watermarks

**Issue**: Out-of-order events
**Workaround**: Configurable `eventsMaxOutOfOrderness`

## Performance Considerations

### Memory Usage
- **Maude Soup State**: Can grow large for long-running traces
- **Flink State**: Configure TTL for automatic cleanup
- **Kafka Consumer**: Buffer size and fetch settings
- **MongoDB Batch**: Tune batch size and interval

### CPU Usage
- **Maude Rewriting**: Bound by `messageRewriteBound` parameter
- **LTLss Evaluation**: Linear in number of events per window
- **Event Processing**: Scales with Flink parallelism

### Network I/O
- **Kafka Consumption**: Tune fetch settings and parallelism
- **MongoDB Writes**: Batch size and connection pooling
- **Inter-Operator Communication**: Flink network buffers

## Security Considerations

### Data Protection
- **Span Data**: May contain sensitive information
- **Maude Terms**: String representation may expose data
- **MongoDB**: Ensure proper authentication and encryption
- **Kafka**: Secure connections and ACLs

### Access Control
- **Flink Web UI**: Secure access in production
- **MongoDB**: Role-based access control
- **Kafka**: SASL/SSL configuration
- **Maude Programs**: Validate external Maude files

### Audit Logging
- **Maude Term Logging**: Optional debug logging to file
- **Flink Metrics**: Monitor processing rates and errors
- **Application Logs**: Structured logging for traceability