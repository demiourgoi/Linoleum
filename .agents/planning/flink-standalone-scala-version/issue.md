# Flink standalone cluster — Scala version mismatch

## Problem

Flink 1.20.x ships only a Scala 2.12 binary distribution. The Linoleum codebase is compiled against Scala 2.13. When submitting via `flink run`, the `PackagedProgram` client classloader has `scala.` **hardcoded** as always-parent-first — no Flink config option can override this. This means:

- Scala 2.12 (from Flink's `flink-dist-1.20.4.jar`) always loads in the client JVM
- Pureconfig (compiled against 2.13) fails with `NoSuchMethodError` for 2.13-only APIs like `scala.reflect.ClassTag$.Char()`

The TaskManager classloaders would respect `classloader.child-first-patterns`, but the client fails before the job graph is even built.

### Why `classloader.*` config options don't help

`PackagedProgram.java` (Flink 1.20) uses a hardcoded array:

```java
private static final String[] DEFAULT_PARENT_FIRST_PATTERNS = {
    "java.", "scala.", "org.apache.flink.", ...
};
```

The config key `classloader.parent-first-patterns.additional` only **appends** to this list. There is no way to **remove** `scala.` from the always-parent-first set for the client classloader. The TaskManager-level classloader reads `classloader.parent-first-patterns.default` and `classloader.child-first-patterns.default` from `flink-conf.yaml`, but the client does not.

### Why `flink-extended/flink-scala-api` doesn't help

This community project provides a Scala 2.13-compatible replacement for Flink's **Scala API** (`flink-scala`, `flink-streaming-scala`). Linoleum does **not** use Flink's Scala API — it calls Flink's Java API from Scala code. The classloader issue is orthogonal.

## Solution

**Downgrade the codebase from Scala 2.13 to Scala 2.12** so it matches Flink's bundled Scala version. No classloader tricks needed.

As a prerequisite, the **`sscheck-core`** dependency must be removed because it is only published for Scala 2.13 on Maven Central. This means removing the `LinoleumFormula` LTL-based property feature, which uses sscheck.

### Impact assessment

- The load testing use case uses `MaudeMonitor` properties only (`MaudeLotrImageGenSafety.yaml`, `MaudeLotrBombadilLiveness.yaml`)
- `LinoleumFormula` is a secondary property type not used in load tests
- All sscheck-related code is **self-contained** in a single file (`SpanStreamEvaluator.scala`, ~260 lines to remove)

## Tasks

### 1. Remove `sscheck-core` dependency from both projects

- [ ] **`linoleum/lib/build.gradle`**: Remove lines 78-80 (the `sscheck-core` `implementation` dependency including its `exclude group: "org.slf4j"`)
- [ ] **`linoleum/lib/build.gradle`**: Remove line 28 (`sscheckVersion = '0.5.2'`)
- [ ] **`linoleum-ltlss-examples/app/build.gradle`**: Remove lines 84-85 (the `sscheck-core` `implementation` dependency)
- [ ] **`linoleum-ltlss-examples/app/build.gradle`**: Remove line 26 (`sscheckVersion = '0.5.2'`)

### 2. Remove sscheck-related code from `linoleum/lib/src/main/scala/.../SpanStreamEvaluator.scala`

- [ ] Remove imports (lines 37-41): `Formula`, `NextFormula`, `Time => SscheckTime` from `io.github.demiourgoi.sscheck.prop.tl`
- [ ] Remove `execute(cfg: LinoleumConfig, formula: LinoleumFormula)` overload (lines 67-69) and its `import PropertyInstances.formulaProperty`
- [ ] Remove the entire formulas package block (lines ~520-620):
  - `type Letter`, `implicit class LetterOps`
  - `type TimedLetter`, `type SscheckFormula`, `type SscheckFormulaSupplier`
  - `def linoleumFormula`
  - `object LinoleumFormula` with `EvaluationConfig` and `apply`
  - `case class LinoleumFormula`
- [ ] Remove `FormulaProperty` object (lines ~694-773):
  - `buildLetters`, `evaluateLetters`, `formulaToTruthValue`
  - Imports at lines 697, 767
- [ ] Remove `implicit val formulaProperty: Property[LinoleumFormula]` (lines ~776-790+)
- [ ] Remove `import formulas._` in `PropertyInstances` (line 691)

### 3. Update Scala 2.13 → 2.12 syntax changes

- [ ] **`linoleum/lib/build.gradle`**: Change `scalaBinaryVersion = '_2.13'` → `'_2.12'` (line 25)
- [ ] **`linoleum-ltlss-examples/app/build.gradle`**: Change `scalaBinaryVersion = '_2.13'` → `'_2.12'` (line 23)
- [ ] Replace `scala.jdk.CollectionConverters._` with `scala.collection.JavaConverters._` in:
  - `SpanStreamEvaluator.scala` (line 3)
  - `source/package.scala` (if used)
  - Any other files using the 2.13 import

### 4. Update test file

- [ ] **`linoleum/lib/src/test/scala/.../LinoleumSpanInfoTest.scala`**: Remove `import io.github.demiourgoi.sscheck.gen.UtilsGen` (line 15) and any test code that uses it

### 5. Remove unused version catalog entries (if applicable)

- [ ] Check `gradle/libs.versions.toml` for `sscheck` references and remove them

### 6. Rebuild and republish

- [ ] `cd linoleum && make publish/local` — republish the linoleum library as `linoleum_2.12`
- [ ] `cd linoleum-ltlss-examples && make flink/build` — rebuild the fat JAR
- [ ] Verify the fat JAR contains Scala 2.12, not 2.13:
  ```bash
  jar tf app/build/libs/app-flink-job.jar | grep "scala/collection/IterableOnce"
  # Should NOT exist in 2.12
  ```
- [ ] Run `make flink/run` to verify submission succeeds

### 7. Clean up Flink config

- [ ] Simplify `flink-cluster/flink-conf.yaml` classloader section to just:
  ```yaml
  classloader.resolve-order: parent-first
  ```
  (remove `child-first-patterns`, `parent-first-patterns` overrides — no longer needed)

## Files affected

| File | Type of change |
|------|---------------|
| `linoleum/lib/build.gradle` | Remove sscheck dep, change scala version |
| `linoleum-ltlss-examples/app/build.gradle` | Remove sscheck dep, change scala version |
| `linoleum/lib/src/main/scala/.../SpanStreamEvaluator.scala` | Remove ~260 lines of formula-related code, fix 2.13→2.12 imports |
| `linoleum/lib/src/test/scala/.../LinoleumSpanInfoTest.scala` | Remove sscheck import |
| `linoleum-ltlss-examples/flink-cluster/flink-conf.yaml` | Simplify classloader config |
| `linoleum-ltlss-examples/app/src/main/resources/log4j2.properties` | No change (ERROR level already set) |
