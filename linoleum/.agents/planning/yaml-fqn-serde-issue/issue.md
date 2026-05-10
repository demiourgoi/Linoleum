# YAML FQN Serialization Issue in `MaudeMonitorConfig`

## Symptom

When running Linoleum via the YAML-based configuration path (`RunMaudeMonitor`) using `linoleum-ltlss-examples/MaudeLotrBombadilLiveness.yaml`, Flink throws an `InvalidProgramException` with "is not serializable" during job setup:

```
org.apache.flink.api.common.InvalidProgramException: Object io.github.demiourgoi.linoleum.evaluator.SpanStreamEvaluator$$Lambda$412/1923634801 is not serializable
  at org.apache.flink.api.java.ClosureCleaner.ensureSerializable(ClosureCleaner.java:211)
  at org.apache.flink.streaming.api.datastream.DataStream.keyBy(DataStream.java:295)
  at io.github.demiourgoi.linoleum.evaluator.SpanStreamEvaluator.apply(SpanStreamEvaluator.scala:1145)
```

The same monitor configured via Scala code (`MainCode.scala`) works correctly.

## Root Cause

The failure occurs in `MaudeMonitorConfig.resolveHookSupplier` and `MaudeMonitorConfig.resolveShouldIgnoreWindow` (file `linoleum/lib/src/main/scala/io/github/demiourgoi/linoleum/config/monitor/package.scala`).

### Broken implementation (lines 88-98)

```scala
private def resolveHookSupplier(fqn: String): () => MaudeHook = {
  val className = fqn.substring(0, lastDot)
  val methodName = fqn.substring(lastDot + 1)
  val clazz = Class.forName(className)
  val method = clazz.getMethod(methodName)          // java.lang.reflect.Method
  () => method.invoke(null).asInstanceOf[MaudeHook]  // captures `method` !
}
```

The lambda `() => method.invoke(...)` captures the **`java.lang.reflect.Method`** object. `java.lang.reflect.Method` does **not** implement `java.io.Serializable`. When Flink's `ClosureCleaner` walks the object graph of the keyBy lambda to verify serializability, it discovers this non-serializable captured field and throws the exception.

The same problem exists in `resolveShouldIgnoreWindow` (lines 101-115).

### Why `MainCode.scala` works

```scala
rlHooks = List((IsPoliteTextOpHook.hookOpName, IsPoliteTextOpHook.apply))
```

`IsPoliteTextOpHook.apply` is a **method reference to a static method** on a companion object. Scala compiles this into a `Function0` that invokes the static method directly — it captures **no closure variables** and the generated anonymous function class extends `scala.Serializable`.

## Proposed Fix

Replace the direct `java.lang.reflect.Method` capture with a **serializable supplier wrapper** that stores only `String` values (class name and method name) and resolves the `Method` lazily after deserialization.

### Key design decisions

1. Store `className: String` and `methodName: String` in the wrapper (both `Serializable`)
2. Use a `@transient lazy val` for the cached `Method` reference so it's re-resolved after Flink deserialization
3. The call-site remains `method.invoke(...)` — no change in invocation semantics
4. Minimal performance impact: one lazy val check per JVM instance per hook (nanoseconds), dwarfed by Maude rewriting cost (microseconds to milliseconds)

### Files to modify

- `linoleum/lib/src/main/scala/io/github/demiourgoi/linoleum/config/monitor/package.scala`
  - Add `SerializableReflectiveSupplier0` helper class (for 0-arg methods)
  - Add `SerializableReflectiveSupplier2` helper class (for 2-arg methods)
  - Rewrite `resolveHookSupplier` to use `SerializableReflectiveSupplier0`
  - Rewrite `resolveShouldIgnoreWindow` to use `SerializableReflectiveSupplier2`