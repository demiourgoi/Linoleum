# Gatling-based Kafka Load Generator for Linoleum

## Overview

A standalone Gradle subproject that generates protobuf-serialized OTEL span
traffic into the `otlp_spans` Kafka topic, using the
[gatling-kafka-plugin](https://github.com/Tinkoff/gatling-kafka-plugin).
Traffic follows a staircase ramp-up pattern across plateaus of
**100 → 500 → 1000 → 5000 → 10000 messages per second**.

## Motivation

Linoleum already emits processing-speed metrics via `spansProcessedCounter`
in `SpanStreamEvaluator`. We need controlled, reproducible input traffic so we
can observe how those metrics respond to increasing load — without having to
manually run lotrbot chat turns or replay sim files.

## Traffic Shape

```
msg/s
^
|                                    ██████████ 10000
|                              ██████
|                        ██████
|                  ██████
|            ██████
|      ██████ 100
|
+──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────→ time
      30s    60s    30s    60s    30s    60s    30s    60s    30s    60s
      ramp  hold   ramp   hold   ramp   hold   ramp   hold   ramp   hold
```

Implemented via Gatling's injection DSL:

```scala
setUp(
  scn.inject(
    rampUsersPerSec(0) to (100) during (30.seconds),
    constantUsersPerSec(100) during (60.seconds),
    rampUsersPerSec(100) to (500) during (30.seconds),
    constantUsersPerSec(500) during (60.seconds),
    rampUsersPerSec(500) to (1000) during (30.seconds),
    constantUsersPerSec(1000) during (60.seconds),
    rampUsersPerSec(1000) to (5000) during (30.seconds),
    constantUsersPerSec(5000) during (60.seconds),
    rampUsersPerSec(5000) to (10000) during (30.seconds),
    constantUsersPerSec(10000) during (60.seconds),
  )
)
```

Each "user" is one Gatling virtual user that executes the scenario once:
build an `ExportTraceServiceRequest` protobuf, serialize it to bytes, and
send it to the Kafka topic `otlp_spans`.

## Architecture

```
gatling-load-gen/                       # new root-level directory
├── build.gradle                        # Java 17+ toolchain, scala 2.13
├── settings.gradle.kts                 # rootProject.name = "gatling-load-gen"
├── Makefile                            # make run target
└── src/main/scala/
    └── io/github/demiourgoi/linoleum/loadgen/
        ├── Main.scala                  # entry point, launches simulation
        ├── SpanTrafficSimulation.scala  # Gatling simulation class
        └── SpanGenerator.scala         # builds ExportTraceServiceRequest protobufs
```

## Key Design Decisions

### 1. Separate subproject with Java 17+

| Aspect | `linoleum/lib` + `linoleum-ltlss-examples` | `gatling-load-gen` |
|---|---|---|
| Java toolchain | 8 (Flink 1.20 requirement) | 17+ (Gatling 3.9.x requirement) |
| Scala binary | 2.13 | 2.13 |
| Build system | Gradle | Gradle (separate settings) |
| Proto classes | From `linoleum` library | From `linoleum` library (Maven local dependency) |

The load generator is not a Flink job — it's a standalone Kafka producer. It
can (and must, for Gatling) target a newer JVM without affecting Linoleum's
Flink compatibility.

### 2. Depend on the `linoleum` library for proto classes

```gradle
// gatling-load-gen/build.gradle
repositories {
    mavenLocal()  // linoleum is published here by `make release`
    mavenCentral()
}

dependencies {
    implementation "io.github.demiourgoi:linoleum_2.13:0.2.0-SNAPSHOT"
    // Gatling + Kafka plugin
    implementation "ru.tinkoff:gatling-kafka-plugin_2.13:0.12.0"
    implementation "io.gatling:gatling-core_2.13:3.9.5"
    implementation "io.gatling.highcharts:gatling-charts-highcharts_2.13:3.9.5"
}
```

This gives direct access to the same proto-generated classes the OTEL
collector serializes:

```
io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
io.opentelemetry.proto.trace.v1.Span
io.opentelemetry.proto.common.v1.{KeyValue, AnyValue}
...
```

No proto compilation or duplication needed.

**Prerequisite**: `cd linoleum && make release` must have been run first to
publish the library to `~/.m2`.

### 3. Span payload: programmatic generation

The `SpanGenerator` constructs realistic `ExportTraceServiceRequest` messages
in code rather than reading from files:

- Random trace IDs (16-byte hex) and span IDs per message — each message
  looks like a distinct trace to Linoleum
- Hierarchical span structure matching the representative pattern from
  `maude_terms.maudes`:
  ```
  root: invoke_agent (parentSpanId = "")
    ├── execute_event_loop_cycle
    │     ├── chat (with gen_ai events: user.message, assistant.message, choice)
    │     └── stream_chat
  ```
- Realistic span names and attributes (`gen_ai.operation.name`,
  `lotrbot.chat_id`, `gen_ai.system: strands-agents`, etc.)
- Nano-timestamps with plausible durations (~1–5 seconds per trace)
- One `ExportTraceServiceRequest` per Gatling user, containing 4 spans
  (matching the representative trace size)

### 4. Plateau transition logging

Gatling does not natively emit events on injection-phase boundaries. A custom
`exec` block in the scenario tracks the current plateau with an
`AtomicInteger` and logs every time it increments:

```
[INFO] Plateau reached: 100 msg/s
[INFO] Plateau reached: 500 msg/s
[INFO] Plateau reached: 1000 msg/s
[INFO] Plateau reached: 5000 msg/s
[INFO] Plateau reached: 10000 msg/s
```

Implementation: a shared `AtomicInteger` phase counter incremented inside an
`exec` block; each Gatling user checks whether the counter changed since last
read (using Gatling's `Session` or a thread-safe static).

### 5. Kafka configuration

| Parameter | Value | Source |
|---|---|---|
| Bootstrap servers | `localhost:9092` | `LocalLinoleumConfig.yaml` |
| Topic | `otlp_spans` | `otel-collector/config.yaml` |
| Encoding | raw protobuf bytes (`otlp_proto`) | `otel-collector/config.yaml` |
| Key serializer | `org.apache.kafka.common.serialization.StringSerializer` | — |
| Value serializer | `org.apache.kafka.common.serialization.ByteArraySerializer` | — |

Configured via the gatling-kafka-plugin's `kafka` protocol DSL:

```scala
val kafkaProtocol = kafka
  .producer
  .topic("otlp_spans")
  .properties(
    Map(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092",
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> classOf[ByteArraySerializer].getName
    )
  )
```

## Files to Create

### `gatling-load-gen/settings.gradle.kts`

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "gatling-load-gen"
```

### `gatling-load-gen/build.gradle`

- Scala 2.13 plugin + application plugin
- Java 17 toolchain (vs Java 8 in linoleum)
- Dependencies: `linoleum_2.13`, `gatling-kafka-plugin`, `gatling-core`,
  `gatling-charts-highcharts`
- `mainClass = "io.github.demiourgoi.linoleum.loadgen.Main"`

### `gatling-load-gen/Makefile`

```makefile
SHELL := /bin/bash
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))
GRADLE := $(ROOT_DIR)/gradlew --no-scan

.PHONY: run build clean

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

build:  ## compile the project
	$(GRADLE) build

run:    ## launch the load generator
	$(GRADLE) run

clean:  ## clean build artifacts
	$(GRADLE) clean
```

### `gatling-load-gen/src/main/scala/.../Main.scala`

Entry point. Parses optional CLI args (Kafka bootstrap servers, topic,
plateau durations) with defaults matching the dev environment. Launches the
Gatling simulation via `Gatling.fromMap()` or the programmatic API.

### `gatling-load-gen/src/main/scala/.../SpanTrafficSimulation.scala`

Gatling `Simulation` class:

- Defines the `kafka` protocol (topic `otlp_spans`, `localhost:9092`,
  `ByteArraySerializer`)
- Scenario: build `ExportTraceServiceRequest` → serialize → send to Kafka
- Injection profile: the staircase ramp described above
- Plateau logging via `exec` block

### `gatling-load-gen/src/main/scala/.../SpanGenerator.scala`

Stateless helper that builds one `ExportTraceServiceRequest` protobuf per
call. Uses the protobuf builder API from the `linoleum` library's generated
classes:

```scala
def generateRequest(): ExportTraceServiceRequest = {
  val traceId = randomHexBytes(16)
  val rootSpan = buildRootSpan(traceId)
  val childSpans = buildChildSpans(traceId, rootSpan.getSpanId)
  ExportTraceServiceRequest.newBuilder()
    .addResourceSpans(...)
    .build()
}
```

## Integration with Existing Workflow

The load generator fits into the existing dev workflow from
`linoleum/DEVELOPER_GUIDE.md`:

```bash
# 1. Publish linoleum locally (one-time)
cd linoleum && make release

# 2. Start local services (Kafka, MongoDB, Jaeger, etc.)
cd linoleum && make compose/start

# 3. Launch the load generator
cd gatling-load-gen && make run

# 4. In another terminal, run Linoleum to process the traffic
cd linoleum-ltlss-examples
source ~/.lotrbot.env
make clean run EXAMPLE=MaudeLotrImageGenSafety.yaml

# 5. Observe metrics in Prometheus
# sum by (job_name) (rate(flink_taskmanager_job_task_operator_linoleum_evaluator_linoleum_spans_processed_total[1m]))
```

## What We Don't Do

- **No Gatling assertions or performance measurements.** We only care about
  generating traffic; the processing side is measured by Linoleum's
  Prometheus metrics.
- **No Avro, no Schema Registry.** Raw protobuf bytes, same as the OTEL
  collector.
- **No cross-dependency from linoleum-ltlss-examples.** The load generator is
  fully standalone.
- **No Gatling HTML reports analysis.** They are generated as a side effect
  (`results/` directory) but are not the primary observability mechanism.

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| `linoleum` library pulls Flink as transitive dependency | Exclude Flink dependencies in `gatling-load-gen/build.gradle`; we only need the proto classes |
| Gatling injects "users" not "messages" cleanly | One user = one message = one `ExportTraceServiceRequest`. The mapping is 1:1. |
| 10000 msg/s may saturate local Kafka | The point is to find the limit. If Kafka can't keep up, the Linoleum processing rate will plateau in Prometheus. |
| Gatling results directory clutter | Add `results/` to `.gitignore`. Gatling's `run` automatically creates timestamped subdirectories. |

## Scaling: Parallelism and Maude

### The Maude bottleneck

The Maude C++ runtime (`bindings:3.5.0.0`) is **not thread-safe**. Linoleum
works around this with a global `synchronized` lock in
`MaudeModules.runWithLock` (`SpanStreamEvaluator.scala:366-370`):

```scala
def runWithLock[T](block: => T): T = maudeRuntime.synchronized { block }
```

This serializes all Maude operations across the JVM. With session windows
keyed by trace ID, Flink can process many windows in parallel — but every
evaluation funnels through the single lock. Higher intra-JVM parallelism only
increases thread contention and can trigger native crashes (the lock doesn't
fully protect against C++-level issues under load).

### Alternatives for scaling

| Approach | Effort | Maude instances | Key constraint |
|---|---|---|---|
| **A) Single JVM, tuned heap** | None (done) | 1 | `synchronized` lock caps throughput; Maude native crashes at high load |
| **B) Multiple `make run` processes** | Medium | N | Requires Kafka partitions >= N; each process is a separate Flink mini-cluster with overhead |
| **C) Standalone Flink cluster, multi-TM** | Medium | M (= TaskManagers) | Each TM is a separate JVM => separate Maude runtime; proper cluster, single job |
| **D) Improved locking (TODO)** | High | 1+ per JVM | Per-module or per-key locks would allow true intra-JVM parallelism |

### Next steps

1. **Single JVM ceiling found**: ~350 spans/s (≈88 traces/s, with 4 spans
   per trace) before Maude native SIGSEGV. This is per-second (`rate()`
   normalizes to /s regardless of the `[1m]` window). Not bad for a
   Maude-based evaluator running on a single JVM with a global lock — and
   with `JAVA_HEAP=16g`, the JVM itself was not the bottleneck. Measured
   with `make clean run EXAMPLE=MaudeLotrImageGenSafety.yaml` (default
   `JAVA_HEAP=16g` from the Makefile).

2. **Multi-JVM via standalone Flink cluster** (immediate next step): Deploy a local Flink cluster with
   multiple TaskManager JVMs, running a single Linoleum job. This is the
   closest approximation of a real cluster:

   ```bash
   # Download Flink 1.20.1
   wget https://dlcdn.apache.org/flink/flink-1.20.1/flink-1.20.1-bin-scala_2.12.tgz
   tar xzf flink-1.20.1-bin-scala_2.12.tgz

   # Configure multiple TMs (each = separate JVM = separate Maude runtime)
   # conf/flink-conf.yaml:
   #   taskmanager.numberOfTaskSlots: 2
   #   taskmanager.memory.process.size: 8g
   #   parallelism.default: 4
   # conf/workers:
   #   localhost
   #   localhost
   #   localhost

   ./bin/start-cluster.sh

   # Submit the Linoleum fat jar
   ./bin/flink run \
     -c io.github.demiourgoi.linoleum.RunMaudeMonitor \
     app/build/libs/app-0.1.1-SNAPSHOT-all.jar \
     /path/to/LocalLinoleumConfig.yaml \
     /path/to/MaudeLotrImageGenSafety.yaml
   ```

   Benefits: genuine multi-JVM parallelism, each TM has its own Maude
   runtime, single job, proper Flink metrics and Web UI at
   `localhost:8081`. Drawback: more setup than `make run`.

3. **Fallback — smaller plateaus**: If multi-JVM also hits Maude stability
   limits, reduce the load generator plateaus to match the ~350 spans/s
   single-JVM ceiling. Suggested: 20 → 50 → 100 → 200 → 350 msg/s
   (80 → 200 → 400 → 800 → 1400 spans/s). Just edit the numbers in
   `SpanTrafficSimulation.scala`.
