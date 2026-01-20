# Technical design 

## Data model

### Events 

Linoleum processes a stream of OTEL spans, converting each span into a SpanStart and SpanEnd event. 
Events are order by their epoch: start events use the span start as epoch, while end events use the end.

## Supported properties

- `LinoleumFormula`: based on a LTLss formula. The sequence of spans is sorted arranged on letters using tumbling windows, and the formula is evalauted on that LTLss word.
- `MaudeMonitorProperty`: based on a Maude program. 
  - We start from a specified Maude soup term, that should contain an object of a class that can be evaluated to a truth value with `|=` as defiend in Maude's `SATISFACTION` module. 
  - For each Linoleum event we create a message and add it to the soup, and rewrite it according to the Maude program.
  - The final truth value is the result of reducing the final soup using `|=` and specified Maude `Prop` term. 

We use Scala type classes for `Property` trait to evaluate those properties on a stream of Linoleum events. 

## Implementation

### Data source

The source is `type SpanInfoStream = DataStream[SpanInfo]` read from Kafka, and using event time extracted as `nanosToMs(spanInfo.getSpan.getStartTimeUnixNano)`

Implemented at lib/src/main/scala/io/github/demiourgoi/linoleum/source/package.scala

### Window processing 

We use [session windows](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/#session-windows), keyed by the a function from SpanInfo to String specified in the property, that by default groups by trace id (hex representation).  

Each window is processed by an instance of `ProcessWindow` that:

- Converts the window into a sequence of Linoleum events
- Determines whether or not to ignore the window
  - For `LinoleumFormula` we ignore all windows but the first one, that contains the root span. This is because we do not support state for this properties
  - For `MaudeMonitorProperty` by default we ignore no window, but the user can optionally supply a window filter. This is because we use the current soup term as the keyed window global state (as returned by `context.globalState()`), so we can support longer properties. However, as we support configuring the state with an optional [Time-To-Live (TTL)](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl), and because there is no way in Flink to distinguish when the key state is initialized by the first time vs after a TTL cleanup, we also support specifying a custom window filtering function.



Implemented at lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala
