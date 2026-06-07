# Implementation notes

## First iteration

Build passes cleanly. All three source files compile:

- `SpanGenerator.scala` — generates realistic `ExportTraceServiceRequest` protobufs
- `SpanTrafficSimulation.scala` — Gatling simulation with staircase ramp-up
- `Main.scala` — entry point, launches via `Gatling.fromMap()`

### What was created

| File | Purpose |
|---|---|
| `gatling-load-gen/settings.gradle.kts` | Gradle settings, root project name |
| `gatling-load-gen/build.gradle` | Java 17+, Scala 2.13, deps on linoleum + gatling-kafka-plugin |
| `gatling-load-gen/Makefile` | `make build`, `make run`, `make clean` |
| `gatling-load-gen/.gitignore` | Excludes `results/`, `.gradle/`, IDE files |
| `.../loadgen/SpanGenerator.scala` | Builds OTEL `ExportTraceServiceRequest` protos with 4 spans each |
| `.../loadgen/SpanTrafficSimulation.scala` | Gatling simulation: injects 100→500→1000→5000→10000 msg/s |
| `.../loadgen/Main.scala` | CLI entry point with `-Dkafka.bootstrap.servers` override |

### Key dependencies resolved

- `ru.tinkoff:gatling-kafka-plugin_2.13:0.12.1` — Kafka producer protocol for Gatling
- `io.gatling:gatling-core:3.9.5` etc. — Gatling 3.9.x (no `_2.13` suffix)
- `io.github.demiourgoi:linoleum_2.13:0.2.0-SNAPSHOT` — OTEL proto classes from Maven local
- `com.sksamuel.avro4s:avro4s-core_2.13:4.1.1` — needed by plugin (provided scope)
- Confluent Maven repo for `kafka-clients:7.4.0-ce`

### To test it

```bash
# 1. Publish linoleum (if not already)
cd linoleum && make release

# 2. Start Kafka + services
make compose/start

# 3. Run load generator (expects Java 17+)
cd gatling-load-gen && make run
```

The simulation runs for ~10 minutes total with plateau logging on each transition. Want me to try running it?
