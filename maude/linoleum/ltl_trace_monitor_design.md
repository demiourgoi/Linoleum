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

## Timed variant: bounded eventually

The hand-written Linoleum monitors express an obligation with a timeout — e.g.
`lotrbot_bombadil_liveness.maude` says "always, when the user mentions Tom
Bombadil then the bot rages within N turns", where a turn is the end of a trace.
The timestamp-based counterpart is implemented in
`ltl_timed_trace_monitor.maude`, which adds a bounded eventually

```
within(N, Q)        *** Q must happen within N nanoseconds
```

so that `[] (P -> within(N, Q))` can be monitored. It imports
`ltl_trace_monitor.maude` and reuses its alphabet, `Verdict`, and helpers; only
the state and the time-aware progression are new.

### Time model

Every message carries an absolute time in nanoseconds:

```
msgTime(spanStart(M, S))  = startTimeUnixNano of the Span wrapped by S
msgTime(spanEnd(M, S))    = endTimeUnixNano   of the Span wrapped by S
```

and the stream is assumed ordered by that time (non-decreasing). No separate
clock is needed: a timed obligation is stored with an **absolute deadline**, so
the next `tconsume` can compare `msgTime(m)` with it.

### Timed progression

`tprogress(now, m, f)` is the one-step derivative at `now = msgTime(m)`; the
untimed rules are unchanged and the new cases are:

```
tprogress(now, m, within(d, p))
    = True              if p holds at m
    = ev(now + d, p)    otherwise        (start a clock; deadline = now + d)

tprogress(now, m, ev(deadline, p))
    = False             if deadline < now   (missed: too late)
    = True              if p holds at m     (discharged)
    = ev(deadline, p)   otherwise           (still pending)
```

`ev(deadline, p)` is an ordinary `Formula` atom as far as the LTL validity
engine is concerned, so the verdict is decided exactly as in the untimed
monitor (`isTaut` on the residual). Because **pending `ev` atoms are free** in
that decision, the monitor never reports a violation before a deadline has
actually passed, and reports `satisfied` only when the residual is valid no
matter how pending obligations resolve. Multiple obligations are tracked
independently, so `[] (P -> within(N, Q))` starts a fresh clock each time `P`
holds.

`p` (the argument of `within`) must be a **state formula** — a boolean
combination of atoms with no temporal operators (e.g. `isSpanEnd`,
`(isRootSpanEnd /\ endNamed("chat"))`). `nowHolds/2` evaluates exactly those.

### API

| Operator | Type | Meaning |
|---|---|---|
| `within` | `Nat Formula ~> Formula` | bounded eventually (user-facing) |
| `msgTime` | `Msg ~> Nat` | absolute time of a message |
| `tinit` | `Formula ~> TMonitor` | build the timed monitor |
| `tconsume` | `Msg TMonitor ~> TMonitor` | advance by one message (reads its time) |
| `tformula` | `TMonitor ~> Formula` | inspect the residual |
| `tverdict` | `TMonitor ~> Verdict` | open-future 3-valued result |
| `tfinalize` | `TMonitor ~> Verdict` | closed-trace result (pending/never-started obligations become violations) |
| `trun` | `MsgList TMonitor ~> TMonitor` | replay a whole list |

Example:

```maude
reduce tinit([] (isSpanStart -> within(1000000000, isSpanEnd))) .
reduce tverdict(tconsume(Msg, tinit(F))) .
reduce tfinalize(trun(Msg1 ;; Msg2 ;; mt, tinit(F))) .
```

### Limits

- `within`'s argument must be a state formula (no nested temporal operators).
- A violation is only reported once a deadline has passed (sound for
  monitoring); as with the untimed monitor, `tverdict` cannot confirm a liveness
  on an open stream — use `tfinalize` at window close.
- Deadlines are absolute nanosecond values, so the semantics follow the span
  clock; if events can arrive with equal timestamps, the deadline comparison is
  inclusive (`deadline < now` is the expiry test).

## Files

- `ltl_trace_monitor.maude` — the untimed library (`omod LTL-TRACE-MONITOR`).
- `ltl_timed_trace_monitor.maude` — the timed library
  (`omod LTL-TIMED-TRACE-MONITOR`, imports the untimed one).
- `ltl_trace_monitor_test.maude` — untimed examples / smoke tests.
- `ltl_timed_trace_monitor_test.maude` — timed examples / smoke tests. Both test
  files also double as Harold diagnostics harnesses because they load the
  dependencies first.

Run from this directory (the one containing `model-checker.maude`):

```
maude < ltl_trace_monitor_test.maude
maude < ltl_timed_trace_monitor_test.maude
```

The expected environment is the Linoleum one: `model-checker.maude` and
`maude/linoleum/trace.maude` loaded before the monitor (the runtime always loads
both, see `maude/DEVELOPER_GUIDE.md`).

## Implementation notes

### Deliverables

| File | Purpose |
|---|---|
| `ltl_trace_monitor.maude` | The untimed monitor library (`omod LTL-TRACE-MONITOR`) |
| `ltl_timed_trace_monitor.maude` | The timed monitor library (`omod LTL-TIMED-TRACE-MONITOR`) |
| `ltl_trace_monitor_test.maude` | Untimed examples / smoke tests; Harold harness |
| `ltl_timed_trace_monitor_test.maude` | Timed examples / smoke tests; Harold harness |
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

### Object-module gotcha

Both libraries declare object patterns such as `< O : Span | startTimeUnixNano
: T >` (and the untimed `startNamed`/`endNamed` patterns). These only match
spans that carry *extra* attributes if the module is an **object module**
(`omod`): a plain `mod` matches the exact attribute set only. The libraries are
therefore `omod`s (closing with `endom`). This surfaced with `msgTime` in the
timed monitor, which silently failed to reduce for real spans that also carry
`name`, `traceId`, etc. (trace.maude's own accessors work because they live in
an `omod`.)

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
- `maude < ltl_timed_trace_monitor_test.maude` runs clean (0 warnings /
  0 errors), all results correct:
  - `start@0; end@100`, `within(1000, ...)` → `satisfied`
  - `start@0; end@2000` → `violated` (late)
  - `start@0`, no end → `violated`
  - `start@0; end@999` and `start@0; end@1000` → `satisfied` (deadline inclusive)
  - `start@0; start@1500; end@1600` → `violated` (first clock timed out) even
    though an end arrives later
- Harold diagnostics: both test files are clean (0/0/0). Checking a library file
  **alone** reports only import-resolution artifacts, because Harold loads just
  that one file; use the test files as the harness.

### Runtime integration

Reuse the existing `MaudeMonitorProperty` plumbing (`SpanStreamEvaluator.scala`):
`initialSoup = init(F)`, replace the per-event "rewrite the soup" with
`consume(<toMaude message>, soup)`, persist the resulting `Monitor` term in
Flink keyed state (with the existing TTL option), and evaluate with `verdict`
(streaming) or `finalize` (window close). For bounded-eventually properties use
the timed operators `tinit` / `tconsume` / `tverdict` / `tfinalize` instead.
Only the advance and evaluate operators change; windowing, keying, TTL, and the
sink stay as they are.

