# Root Cause Analysis: SIGSEGV in native Maude code under load

## Crash description

Running a Flink job with `MaudeMonitor`-based properties under heavy load eventually crashes with:

```
# A fatal error has been detected by the Java Runtime Environment:
#  SIGSEGV (0xb) at pc=0x00007fea48080168, ...
# Problematic frame:
# C  0x00007fea48080168
# The crash happened outside the Java Virtual Machine in native code.
```

The crash occurs after processing many spans/events. The `monitorModule.delete()` change (described below) was an attempted fix but did not resolve the crash; it has been reverted.

## Native object lifecycle (SWIG bindings)

Two key C++ classes underpin the Java bindings:

### `VisibleModule` (wrapped as Java `Module`)
- **Source**: `maude_wrappers.cc:265-278`, `getModule(name)` returns `premodule->getFlatModule()` — a **singleton** pointer owned by the Maude interpreter. It is NOT heap-allocated per-request; each call returns the same `VisibleModule*`.
- `getModule(name)` calls `vmod->protect()` on the returned pointer.
- The SWIG `%newobject getModule` annotation (in `maude.i:99`) tells SWIG the Java wrapper owns the pointer, so `Module.delete()` calls `delete (VisibleModule*)ptr`, whose `%extend` destructor (`module.i:36-44`) calls `$self->unprotect()` and then the C++ `delete` operator **frees the singleton**.
- **Consequence**: `Module.delete()` frees an object still owned by the Maude interpreter. Any subsequent use of that pointer (via another `getModule` call, or via Terms referencing it) is use-after-free → SIGSEGV.

### `EasyTerm` (wrapped as Java `Term`)
- **Source**: `easyTerm.cc:56-77`. Constructor calls `protect()` on the module; destructor calls `unprotect()` on the module and frees the internal `DagNode*`/`Term*`.
- Created by `Module.parseTerm(...)` (marked `%newobject` in `module.i:24`). Each call heap-allocates a new `EasyTerm*`.
- `Term.delete()` → `delete_Term(swigCPtr)` → `delete (EasyTerm*)ptr` → `~EasyTerm()` → frees native memory.

### Protect/unprotect balance per evaluation

| Operation | Module protect Δ |
|-----------|-----------------|
| `getModule(name)` | +1 |
| `parseTerm(...)` (EasyTerm ctor) | +1 |
| `Module.delete()` (explicit or via GC `finalize()`) | -1 + **frees VisibleModule** |
| `Term.delete()` (explicit or via GC `finalize()`) | -1 |

## Issues found

### 🟠 Issue #1 — `soup` Terms abandoned in the loop (lines 885-905)

Each reassignment of `soup` in the event loop discards the previous `Term` without explicit deletion:

```scala
var soup = monitorModule.parseTerm(initialSoup)   // Term #1 (native heap)
orderedEvents.foreach { event =>
    soup = monitorModule.parseTerm(nextSoup)       // Term #2; Term #1 leaked
    // ...
}
```

Each `parseTerm` heap-allocates a new native `EasyTerm`. The old one is no longer referenced, so it waits for JVM GC → `finalize()` → `delete()` → native free. __Under heavy load__ (many events per window × many concurrent keys), hundreds/thousands of __native `EasyTerm` objects accumulate before GC triggers__. The native C++ heap can exhaust before the JVM decides to GC, causing allocation failures in Maude's internal code → SIGSEGV.

### 🟠 Issue #2 — `truthTerm` leaked in `soupToTruthValue` (lines 796-805)

```scala
def soupToTruthValue(propertyModule: Module, maudeProperty: String)(soup: Term): TruthValue = {
    val truthTerm = propertyModule.parseTerm(s"""${soup.toString} |= $maudeProperty""")
    truthTerm.reduce()
    truthTerm.toString match { ... }
    // truthTerm never explicitly deleted → waits for GC
}
```

Called at least once per window evaluation (line 924), plus once per event when `onEvaluationStep` is active (line 920). Same accumulation problem as Issue #1.

### 🔴 Issue #3 — Module wrappers freed by GC `finalize()`

Even without explicit `monitorModule.delete()`, the Module wrapper created by `loadModule` (line 291, `jMaude.getModule(moduleName)`) is a local variable in `ensureModulesLoaded` / `runWithLock`. When it goes out of scope and GC runs, `finalize()` → `delete()` → frees the `VisibleModule` singleton. This creates a use-after-free hazard for:

- **Subsequent evaluations** that call `getModule(moduleName)` → returns dangling pointer.
- **Outstanding Terms** from the same module — when their `finalize()` runs, `~EasyTerm()` accesses `symbol()->getModule()` → dangling pointer.

Since `runWithLock` serializes all Maude access, the GC thread calling `delete_Module` asynchronously is a race condition with the locked thread doing `parseTerm`/`rewrite`/`reduce`.

### 🟡 Issue #4 — `IsPoliteTextOpHook.run` (example code, lower priority)

In `IsPoliteTextOpHook.scala:71-94`, `term.symbol()`, `.getModule()`, `textArg.getSort()` all create owned native wrappers (`Symbol`, `Module`, `Sort`) that are never explicitly deleted.

## Proposed fix

### 1. Cache Module wrappers in `MaudeModules` (fixes Issue #3)

Add a `ConcurrentHashMap[String, MaudeModule]` to `MaudeModules`, keyed by `resourcePath + "/" + moduleName`. Before calling `jMaude.getModule()`, check the cache. Since the returned `VisibleModule*` is a singleton, caching ensures:
- Only one Java `Module` wrapper exists per underlying Maude module.
- The wrapper stays alive for the JVM lifetime (referenced by the cache), so GC never calls `finalize()` → `delete()` on it.
- No use-after-free of the `VisibleModule*` pointer.

Key format:
- Regular resources: `resourcePath + "/" + moduleName` (e.g., `"maude/linoleum/trace.maude/TRACE-CLASS-OBJECTS"`)
- Stdlib resources: `MAUDE_STDLIB_RESOURCE_PREFIX + fileName + "/" + moduleName` (e.g., `"maude/stdlib/model-checker.maude/SATISFACTION"`)

The existing `lazy val` modules (`traceTypesModule`, `satisfactionModule`, `jsonModule`) can either be migrated to use the same cache or kept as-is (they already serve the caching purpose for those specific modules).

**Thread safety**: The cache is read/written inside `runWithLock` (synchronized on `maudeRuntime`), so a non-concurrent map would also be safe. However, using `ConcurrentHashMap` or `TrieMap` adds defense-in-depth.

### 2. Explicitly delete Terms (fixes Issues #1 and #2)

- In the `evaluateWithCallback` loop, call `oldSoup.delete()` before reassigning `soup`.
- In `soupToTruthValue`, call `truthTerm.delete()` after extracting the result string.
- In `evaluateWithSteps`, the `onEvaluationStep` callback captures the `soup` Term reference — but the Term is still alive (referenced by `soup` in the loop), so no extra deletion needed there.

### 3. (Optional, lower priority) Clean up `IsPoliteTextOpHook`

Explicitly delete `Symbol`, `Module`, and `Sort` objects created in the hook's `run` method.

## Implementation steps

1. Add `private val moduleCache: TrieMap[String, MaudeModule]` to `MaudeModules`.
2. Add a private `cachedLoadModule(resourcePath: String, moduleName: String): MaudeModule` method.
3. Refactor `loadModule` and `loadStdLibModule` to use the cached method.
4. In `evaluateWithCallback`: delete previous `soup` before reassignment in the loop.
5. In `soupToTruthValue`: delete `truthTerm` after extracting the result.
6. Run tests to verify no regressions.
