# Review Notes

## Consistency Check

### ✅ Consistent Areas

- **Dependency versions:** All dependency references across `codebase_info.md`, `dependencies.md`, and `architecture.md` are consistent.
- **Span tree structure:** The 4-span tree (root → invoke_agent, children → loop, chat, stream_chat) is consistently described across `components.md`, `data_models.md`, and `workflows.md`.
- **Kafka configuration:** Topic name (`otlp_spans`), serializer classes, and producer settings are consistent across `interfaces.md`, `components.md`, and `architecture.md`.
- **Injection profile:** The staircase ramp-up schedule (100→500→1000→5000→10000) is consistently documented in `architecture.md`, `workflows.md`, and `components.md`.
- **Entry point and launch:** The programmatic Gatling launch via `Main.scala` is consistently described.

### ⚠️ Minor Inconsistencies Noted

1. **`stream_chat` span kind:** The code uses `SPAN_KIND_CLIENT` for `stream_chat` while all other spans use `SPAN_KIND_INTERNAL`. This is intentional (it represents an HTTP client call to `api.mistral.ai`) and is correctly documented, but could confuse readers who expect consistent span kinds.

2. **Repositories:** `dependencies.md` lists Confluent repo at `https://packages.confluent.io/maven/`. The `build.gradle` uses `uri("https://packages.confluent.io/maven/")`. These are equivalent. ✅ Consistent.

## Completeness Check

### ✅ Well-Covered Areas

- Component responsibilities and structure
- Data model (protobuf message layout, span attributes, events)
- Kafka producer configuration
- Injection profile and timing
- Dependency management (including exclusion rationale)
- Build and run instructions

### 🔍 Areas Lacking Detail

1. **Error handling:** The codebase contains no explicit error handling. There is no retry logic, dead-letter handling, or health checking. This is acceptable for a load generator but could be documented.

2. **Gatling results/reporting:** The simulation stores results in `results/` directory but there is no documentation on how to interpret Gatling reports, what metrics are collected, or how to analyze them.

3. **Scaling/performance tuning:** The producer settings (`linger.ms=5`, `batch.size=16384`, `acks=0`) are documented but there is no discussion of how these values were chosen or how to tune them for different environments.

4. **Monitoring/Observability:** The load generator itself produces no telemetry. There is no documentation on how to monitor the health of the load generator during execution.

5. **Custom Kafka topic:** The topic name `otlp_spans` is hardcoded in `SpanTrafficSimulation.scala`. There is no JVM property override (unlike `kafka.bootstrap.servers` which is configurable).

### 🚫 Out of Scope (Intentional Gaps)

- The `linoleum` library internals — documented in the `linoleum/` project
- The consumer side (who reads from `otlp_spans`) — belongs to the Linoleum runtime verification pipeline

## Recommendations

1. Add JVM property override for the Kafka topic name (similar to `kafka.bootstrap.servers`).
2. Document the expected consumer of the `otlp_spans` topic (which Linoleum component consumes these traces).
3. Add a brief section on tuning producer settings for different throughput targets.
4. Consider adding a simple health check or progress reporting beyond plateau logging.
