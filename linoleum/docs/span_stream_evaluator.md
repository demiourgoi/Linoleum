# SpanStreamEvaluator.scala Analysis

The `SpanStreamEvaluator.scala` file is a core component of the Linoleum system that handles the evaluation of temporal logic formulas against streams of OpenTelemetry spans. Here's a detailed breakdown of its functionality:

## Package Structure

The file defines several packages and objects:
- `formulas` package object: Defines the core data types and operations for formula evaluation
- `evaluator` package object: Defines the stream processing types
- `SpanStreamEvaluator` class: The main class that processes span streams
- Supporting classes and utilities

## Key Components

### 1. Event Model

The file defines a model for span events:

- `LinoleumEvent` trait: Represents events in the system with two implementations:
  - `SpanStart`: Represents the start of a span, using the span's start time
  - `SpanEnd`: Represents the end of a span, using the span's end time

These events are ordered by their timestamp (`epochUnixNano`) to create a linearized sequence of events from the span tree.

### 2. Letter Construction

A key concept in the file is the notion of a "Letter":

- `Letter`: A list of `LinoleumEvent` objects ordered by time
- `TimedLetter`: A tuple of `(SscheckTime, Letter)` that associates a time with a letter

The system constructs letters by:
1. Collecting all span events for a trace
2. Identifying the root span
3. Ordering events by timestamp
4. Grouping events into time windows (letters) based on a configurable tick period

### 3. Formula Evaluation

The file implements the evaluation of LTLss formulas:

- `FormulaValue`: Represents the result of formula evaluation (True, False, or Undecided)
- `EvaluatedTrace`: Stores the result of evaluating a formula against a trace, including:
  - Trace ID
  - Start time
  - Formula name
  - Evaluation result

### 4. Stream Processing

The core of the file is the `SpanStreamEvaluator` class, which:

1. Takes a stream of span information as input
2. Groups spans by trace ID
3. Uses Flink's session windows to determine when enough spans have been collected
4. Processes each window to:
   - Collect and order events
   - Build letters
   - Evaluate the formula
   - Output the evaluation result

### 5. Window Processing

The `ProcessWindow` inner class handles the processing of each window:

- `collectLinoleumEvents`: Extracts events from spans, identifying the root span
- `buildLetters`: Organizes events into discrete letters based on time windows
- `evaluateFormula`: Applies the LTLss formula to the sequence of letters

## Error Handling

The file includes error handling for various scenarios:
- Multiple root spans in a trace
- Late-arriving spans
- Duplicate spans

## Technical Details

- Uses Flink's windowing mechanisms to handle the streaming nature of span data
- Implements serialization for MongoDB storage
- Provides debugging and logging throughout the process
- Handles time conversion between different time representations (nanoseconds, milliseconds, etc.)

## Integration Points

The `SpanStreamEvaluator` integrates with:
- The source package that provides the span stream
- The LTLss formula evaluation from the sscheck library
- MongoDB for storing evaluation results

This file represents the core processing logic that transforms raw span data into meaningful formula evaluations, forming the heart of Linoleum's runtime verification capabilities.
