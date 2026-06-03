# Workflows

## Primary Workflow: Load Generation

```mermaid
sequenceDiagram
    participant User
    participant Main
    participant GatlingEngine
    participant Simulation
    participant SpanGen as SpanGenerator
    participant Kafka

    User->>Main: make run (or java -jar ...)
    Main->>Main: Read kafka.bootstrap.servers
    Main->>Main: Print startup info
    Main->>GatlingEngine: Gatling.fromMap(props)
    GatlingEngine->>Simulation: Start SpanTrafficSimulation

    loop Staircase Ramp-Up (450 seconds)
        GatlingEngine->>Simulation: Increment virtual users
        Simulation->>Simulation: Log plateau transition (if changed)
        Simulation->>SpanGen: generateRequest()
        SpanGen->>SpanGen: Create traceId, spanIds, chatId
        SpanGen->>SpanGen: Build 4 spans with attributes & events
        SpanGen-->>Simulation: ExportTraceServiceRequest
        Simulation->>Simulation: Serialize to byte array
        Simulation->>Kafka: send(key=UUID, value=bytes)
    end

    GatlingEngine-->>Main: Simulation complete
    Main->>User: Process exits
```

## Detailed Step: Span Generation

1. Generate random 16-byte `traceId` and four 8-byte `spanId`s.
2. Compute `baseTime` from an atomic counter (+10s per request).
3. Create a unique `chatId` as `lotrbot/<UUID>/<counter>`.
4. Build the protobuf message:
   a. **Resource** with `service.name=lotrbot`, SDK attributes.
   b. **InstrumentationScope** with `strands-agents` v0.1.0.
   c. Four spans with parent-child relationships, attributes, and random durations.
5. Return the complete `ExportTraceServiceRequest`.

## Detailed Step: Kafka Publishing (per virtual user)

1. On first user execution, capture `simulationStartMs`.
2. Check elapsed time and log plateau phase transitions.
3. Call `SpanGenerator.generateRequest()`.
4. Create a random UUID as the Kafka key.
5. Serialize the request via `.toByteArray` (protobuf).
6. Publish to Kafka topic `otlp_spans`.

## Injection Profile Timeline

```mermaid
gantt
    title Staircase Ramp-Up (450 seconds)
    dateFormat X
    axisFormat %s s
    section Throughput
    Ramp 0→100    :0, 30s
    Hold 100      :30, 60s
    Ramp 100→500  :90, 30s
    Hold 500      :120, 60s
    Ramp 500→1000 :180, 30s
    Hold 1000     :210, 60s
    Ramp 1000→5000:270, 30s
    Hold 5000     :300, 60s
    Ramp 5000→10000:360, 30s
    Hold 10000    :390, 60s
```

## Setup Workflow (Prerequisites)

```mermaid
flowchart TD
    A[Build & publish linoleum] --> B[Start Kafka + infra]
    B --> C[Build gatling-load-gen]
    C --> D[Run load generator]

    A1[cd linoleum && make release] --> A
    B1[cd linoleum && make compose/start] --> B
    C1[cd gatling-load-gen && make build] --> C
    D1[cd gatling-load-gen && make run] --> D
```
