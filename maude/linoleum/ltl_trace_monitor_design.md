# LTL trace monitor — technical design

Design of `ltl_trace_monitor.maude`, a Maude program for **model-based trace
checking** of Linoleum spans. It is written in the style of
[`linoleum/docs/design.md`](../../../git/Linoleum/linoleum/docs/design.md) and
uses the LTL machinery of `model-checker.maude`. It complements the existing
`MaudeMonitorProperty` by adding a property whose state is an **LTL residual**
rather than a hand-written monitor state machine.

## Goal

Given a stream of `spanStart` / `spanEnd` messages (as defined in
`maude/linoleum/trace.maude`) and an LTL formula whose atoms talk about those
messages, decide whether the trace satisfies the formula — **incrementally**,
so an unbounded production stream can be monitored message by message.

### Requirements → design

| Requirement | Design element |
|---|---|
| Consume one message, return the mutated formula/automaton | `consume : Msg Monitor ~> Monitor` |
| Evaluate the current state of the formula | `verdict : Monitor ~> Verdict` |
| Unbounded sequence of events | residual state machine; each message advances it, past events never revisited |
| Use the LTL model checker | formula language + negative normal form from `LTL`/`LTL-SIMPLIFIER`; validity decided by `SAT-SOLVER`'s `tautCheck` |
| Messages are the "words" | one message = one letter; atoms are boolean predicates over a message |

## Data model

- **Messages**: the LinoleumEvent messages `spanStart(Oid, Object)` and
  `spanEnd(Oid, Object)` from `trace.maude` (sort `Msg`).
- **Letter**: one message, so a trace is a sequence of messages. (The runtime
  can also feed a *group* of messages sharing a timestamp by consuming them one
  at a time; each stays a letter.)
- **Atom**: a named boolean predicate over a message, e.g. `isSpanStart`,
  `isSpanEnd`, `isRootSpanStart`, `startNamed("chat")`. Atoms form a subsort of
  `Formula` and satisfy `_holds_ : Atom Msg ~> Bool`.

## Architecture

```
 spanStart/spanEnd ...        consume/2                verdict/2
        │                        │                         │
        ▼                        ▼                         ▼
   message m  ──────────►  Monitor = monitor(r)  ──────────► satisfied | violated | undecided
                              residual r
```

The monitor state is just the **residual formula** `r`. `consume(m, monitor(r))`
replaces `r` by `progress(m, r)`; `verdict(monitor(r))` decides `r`.

### Progression (one-step derivative)

`progress(m, f)` is the derivative `D_m(f)` characterised by

> `m · u ⊨ f  ⟺  u ⊨ D_m(f)`  for every continuation `u`.

The equations (all in negative normal form) are:

```
progress(m, True)   = True
progress(m, False)  = False
progress(m, ~ f)    = not(progress(m, f))
progress(m, f /\ g) = progress(m, f) /\ progress(m, g)
progress(m, f \/ g) = progress(m, f) \/ progress(m, g)
progress(m, a)      = True   if a holds in m, else False       (a atomic)
progress(m, O f)    = f
progress(m, f U g)  = progress(m, g) \/ (progress(m, f) /\ (f U g))
progress(m, f R g)  = progress(m, g) /\ (progress(m, f) \/ (f R g))
```

There is deliberately **no `O`** around the recursive `f U g` / `f R g`. This is
a correctness point: with `O(f U g)` the obligation would be shifted one message
too far, and an until discharged at the current position would be lost. The test
suite includes a regression for this (a `start;end` trace must discharge
`<> end`).

### Verdict via the LTL validity engine

A residual `r` describes exactly the set of infinite continuations satisfying the
original formula. Deciding that set is LTL validity/satisfiability:

```
eval(r) = satisfied   if r is a tautology
          violated    if ~r is a tautology      (r unsatisfiable)
          undecided   otherwise
```

`tautCheck` (from `SAT-SOLVER`, the native engine also behind `modelCheck`)
implements this over infinite words, so no per-prefix search of the system is
needed. The residual *is* the automaton state: it is the syntactic counterpart
of the accepting-product state that `modelCheck` explores.

### Open-future vs. closed trace

Because the model checker uses infinite-trace (Büchi) semantics, a finite prefix
can only ever **refute** a safety-like obligation. A liveness under an always,
e.g. `[] (start -> <> end)`, stays `undecided` until the trace is closed, because
the unobserved future could still violate it. For a closed window there is a
second operator:

```
finalize(r) = evaluate r on the constant suffix where no atom ever holds
            = satisfied if wk(r) else violated
```

`wk` collapses `O f` to `f`, and on a constant suffix both `f U g` and `f R g`
collapse to `g` (a state that fails once fails forever). This is the
"once the window is closed, what is left of the obligation" semantics and gives
a definite answer for offline/finite trace checking.

## API

| Operator | Type | Meaning |
|---|---|---|
| `init` | `Formula ~> Monitor` | normalise `f` (NNF + simplifier) and build the initial monitor |
| `consume` | `Msg Monitor ~> Monitor` | advance the residual by one message |
| `formula` | `Monitor ~> Formula` | inspect the current residual |
| `verdict` | `Monitor ~> Verdict` | open-future 3-valued result (`satisfied`/`violated`/`undecided`) |
| `finalize` | `Monitor ~> Verdict` | closed-trace 2-valued result |
| `run` | `MsgList Monitor ~> Monitor` | replay a whole list (convenience) |
| `_holds_` | `Atom Msg ~> Bool` | **extension point** for new atoms |

Example:

```maude
reduce init([] (isSpanStart -> <> isSpanEnd)) .
reduce verdict(consume(Msg2, consume(Msg1, init(F)))) .
reduce finalize(run(Msg1 ;; Msg2 ;; Msg3 ;; mt, init(F))) .
```

Adding an atom:

```maude
op isImageGenEnd : -> Atom [ctor] .
eq isImageGenEnd holds spanEnd(M, S) = isImageGenerationSpan(S) .
```

The library declares the built-in atoms (`isSpanStart`, `isSpanEnd`,
`isRootSpanStart`, `isRootSpanEnd`, `startNamed`, `endNamed`); users add more by
importing `LTL-TRACE-MONITOR` and writing equations for `_holds_`.

## Streaming behaviour, cost, limits

- **State**: one formula. `consume` is a pure function of `(message, residual)`.
- **No history**: past messages are never re-examined; only the residual carries
  forward. This is what makes an unbounded stream tractable.
- **Cost per message**: one `progress` reduction (formula-sized) plus two
  `tautCheck` calls for `verdict`. `finalize` is a cheap syntactic evaluation and
  can be used when a per-message semantic verdict is not required.
- **Residual growth**: carrying `f U g` / `f R g` can grow the residual; the
  included `LTL-SIMPLIFIER` (subsumption, `O p /\ O q = O (p /\ q)`, etc.) keeps
  it small in practice. The stress test (`pairs(10)`) stays linear.
- **Undecidability of the future**: `verdict` cannot confirm a liveness property
  on an open stream; use `finalize` at window close for a definite answer.

## Integration with the Linoleum runtime

The existing `MaudeMonitorProperty` (`SpanStreamEvaluator.scala`) already
supports arbitrary stateful Maude soups: it prepends each event, rewrites, and
evaluates `soup |= prop`. An LTL monitor can be exposed as a new property kind
that shares that plumbing:

1. `initialSoup` = `init(F)` rendered as a term; the soup string is the residual.
2. For each `LinoleumEvent`, call `consume(<toMaude message>, soup)` instead of a
   generic rewrite, then persist the resulting `Monitor` term in Flink keyed
   state (as today, optionally with TTL).
3. Evaluate with `verdict` (streaming) or `finalize` (window close).

This keeps the same windowing, keying, TTL, and sink machinery, and only changes
the "advance" and "evaluate" operators. A production implementation would likely
compile the residual to a canonical automaton state to bound its size, which is
the natural next step (see alternatives).

## Alternatives considered

- **Batch `modelCheck` over a trace model.** Build a Kripke structure from the
  whole message list (with stutter extension) and call `modelCheck`. Correct and
  literally uses the model checker, but it is offline, needs the whole trace up
  front, and re-explores it for every window — unsuitable for unbounded streams.
  It is worth keeping as a cross-check on finite traces.
- **Compile the formula once to a Büchi automaton**, then step the automaton.
  Same interface (`consume`/`verdict`) but bounded state; more implementation
  work. The residual monitor is the half-way point that reuses the model
  checker's solver directly.

## Files

- `ltl_trace_monitor.maude` — the library (`mod LTL-TRACE-MONITOR`).
- `ltl_trace_monitor_test.maude` — runnable examples and smoke tests; also a
  Harold diagnostics harness because it loads the dependencies first.

Run from this directory (the one containing `model-checker.maude`):

```
maude < ltl_trace_monitor_test.maude
```

The expected environment is the Linoleum one: `model-checker.maude` and
`maude/linoleum/trace.maude` loaded before the monitor (the runtime always loads
both, see `maude/DEVELOPER_GUIDE.md`).

## Implementation notes

### Deliverables

| File | Purpose |
|---|---|
| `ltl_trace_monitor.maude` | The monitor library (`mod LTL-TRACE-MONITOR`) |
| `ltl_trace_monitor_test.maude` | Runnable examples / smoke tests; doubles as the Harold harness (it loads the deps first) |
| `ltl_trace_monitor_design.md` | This design document |

### Shape of the implementation

```
spanStart/spanEnd messages   consume(msg, monitor(r))        verdict / finalize
        │                            │                              │
        ▼                            ▼                              ▼
  one message = one letter    residual formula r  ─────────► satisfied | violated | undecided
```

The state is just the residual formula, so `consume` is a pure function of
`(message, residual)` and the past is never revisited.

### Correctness bug found and fixed during implementation

The first cut used the progression rule that wraps the recursive until/release
in `O`, i.e. `D_m(f U g) = D_m(g) \/ (D_m(f) /\ O(f U g))`. That shifts the
obligation one message too far and **loses the current position whenever the
until is discharged there**, so `start;end` failed to satisfy `<> end`. The
correct one-step derivative carries the original `f U g` (no `O`) and the same
for `R`; this is what the library implements, and `ltl_trace_monitor_test.maude`
contains a regression for `start;end` discharging a liveness.

### Semantic caveat (worth reviewing)

Under infinite-trace (Büchi) semantics a finite prefix can only **refute**
safety-like obligations; a liveness under an always such as
`[] (start -> <> end)` stays `undecided` on an open stream because the
unobserved future could still violate it. `finalize` supplies the closed-trace
answer under the "no further events" assumption. This is the main semantic
decision to confirm for production use.

### Verification performed

- `maude < ltl_trace_monitor_test.maude` runs clean (0 warnings / 0 errors,
  14 results), all results correct:
  - `start;end` → `undecided` (open) / `satisfied` (`finalize`)
  - `end;end` → `violated`
  - the attribute atom `opNamed("chat")` behaves correctly
  - a generated 20-message trace stays linear (~850 rewrites)
- Harold diagnostics: `ltl_trace_monitor_test.maude` is clean (0/0/0). Checking
  `ltl_trace_monitor.maude` **alone** reports only import-resolution artifacts,
  because Harold loads just that one file; use the test file as the harness.

### Runtime integration

Reuse the existing `MaudeMonitorProperty` plumbing (`SpanStreamEvaluator.scala`):
`initialSoup = init(F)`, replace the per-event "rewrite the soup" with
`consume(<toMaude message>, soup)`, persist the resulting `Monitor` term in
Flink keyed state (with the existing TTL option), and evaluate with `verdict`
(streaming) or `finalize` (window close). Only the advance and evaluate operators
change; windowing, keying, TTL, and the sink stay as they are.

