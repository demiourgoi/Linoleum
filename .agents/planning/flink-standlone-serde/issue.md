# Flink standalone cluster — Kryo serialization for protobuf types

## Problem

When running Linoleum in Flink standalone mode (`localFlinkEnv: false`), jobs failed immediately with:

```
com.esotericsoftware.kryo.KryoException: java.lang.UnsupportedOperationException
Serialization trace:
attributes_ (io.opentelemetry.proto.resource.v1.Resource)
resource_ (io.opentelemetry.proto.trace.v1.ResourceSpans)
resourceSpans_ (io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest)
Caused by: java.lang.UnsupportedOperationException
    at java.util.Collections$UnmodifiableCollection.add(Collections.java:1057)
    at com.esotericsoftware.kryo.serializers.CollectionSerializer.read(...)
```

Kryo's default `FieldSerializer` / `CollectionSerializer` cannot deserialize protobuf generated types because protobuf's repeated fields return unmodifiable collections.

Local mode worked because `addSerdeOptions(config)` in `source/package.scala` sets `pipeline.serialization-config` programmatically on the `Configuration` object before creating the local `StreamExecutionEnvironment`. In standalone mode, this config was supposed to come from `flink-conf.yaml`, but the YAML format was incorrect.

A secondary issue: the TMs could not connect to the JobManager because `jobmanager.rpc.address` was set to `localhost.localdomain`, which resolved to IPv6 `::1` only, and Pekko (Flink's RPC layer) could not establish connections over that address.

## Root causes

### 1. YAML format for `pipeline.serialization-config`

The original `flink-conf.yaml` used YAML nested mappings:

```yaml
pipeline.serialization-config:
  - io.opentelemetry.proto.resource.v1.Resource:
      {
        type: kryo,
        kryo-type: registered,
        class: com.twitter.chill.protobuf.ProtobufSerializer,
      }
```

However, `PipelineOptions.SERIALIZATION_CONFIG` is `ConfigOption<List<String>>` — it expects a **list of strings**, not a list of nested maps. SnakeYAML parsed the nested mappings incorrectly, spilling `class`, `type`, `kryo-type` as orphaned top-level config keys. The serialization config was never applied.

Additionally, Flink 1.20's standard YAML config parser **requires** the file to be named `config.yaml` (not `flink-conf.yaml`) for `pipeline.serialization-config` support. When the old format name was used, Flink rejected the config with:

```
pipeline.serialization-config is only supported with the standard YAML config parser,
please use "config.yaml" as the config file.
```

### 2. IPv6-only hostname resolution

`jobmanager.rpc.address: localhost.localdomain` resolved to `::1` (IPv6 loopback) in `/etc/hosts` with no IPv4 mapping. The JobManager's Pekko actor system bound to `kymera` (the actual hostname), while TMs tried to connect to `localhost.localdomain`. This hostname mismatch caused the TMs to never register.

```
JM log: inbound addresses are [pekko.tcp://flink@kymera:6123]
TM log: Connecting to pekko.tcp://flink@localhost.localdomain:6123...
TM log: Could not resolve ResourceManager address
```

### 3. `registerTypeWithKryoSerializer` doesn't work in standalone mode

Attempting to use `ExecutionConfig.registerTypeWithKryoSerializer(Class<?>, Class<?>)` on the client side did not work because Java `Class` objects are serialized via `ObjectOutputStream` and deserialized on the TM side using the **parent classloader** (Flink's runtime), which cannot resolve classes from the user fat JAR. The `Class` references were silently lost during `JobGraph` → `TaskDeploymentDescriptor` serialization.

## Fix

### 1. Fix `pipeline.serialization-config` YAML format

Per the [Flink docs](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/serialization/third_party_serializers/), use a bracket-delimited single string:

```yaml
pipeline.serialization-config: "[io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest: {type: kryo, kryo-type: registered, class: com.twitter.chill.protobuf.ProtobufSerializer}, io.opentelemetry.proto.trace.v1.ResourceSpans: {type: kryo, kryo-type: registered, class: com.twitter.chill.protobuf.ProtobufSerializer}, ...]"
```

### 2. Use `config.yaml` filename

Flink 1.20 requires the standard YAML parser for `pipeline.serialization-config`, which only activates when the config file is named `config.yaml`. Updated `linoleum-ltlss-examples/Makefile`:

- Copy `flink-cluster/flink-conf.yaml` → `$FLINK_HOME/conf/config.yaml`
- Remove old `$FLINK_HOME/conf/flink-conf.yaml` to avoid conflicts

### 3. Fix hostname resolution

Changed `jobmanager.rpc.address` from `localhost.localdomain` to `kymera` (the actual hostname) so that the JM's Pekko bind address matches what the TMs connect to.

### 4. Don't add chill jars to Flink's `lib/`

The `ProtobufSerializer` class is resolved from the user fat JAR via `pipeline.serialization-config` class name strings. Adding `chill-protobuf.jar` to Flink's `lib/` causes version conflicts with Flink's bundled `chill` and introduces missing dependency issues (`protobuf-java` not in parent classloader).

## Files changed

| File | Change |
|------|--------|
| `linoleum-ltlss-examples/flink-cluster/flink-conf.yaml` | Fixed `jobmanager.rpc.address: kymera`; rewrote `pipeline.serialization-config` as bracket-delimited string |
| `linoleum-ltlss-examples/Makefile` | Copy config to `config.yaml`; remove old `flink-conf.yaml` |
| `linoleum/lib/src/main/scala/.../source/package.scala` | Reverted — local mode keeps `addSerdeOptions`, standalone relies on YAML config |
