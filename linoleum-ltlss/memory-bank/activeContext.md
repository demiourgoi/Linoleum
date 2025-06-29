# Current Focus Areas

## Recent Changes

- Implemented basic span processing pipeline
- Set up Flink job skeleton
- Created MongoDB connection handler
- Refactored MongoDB sink into LinoleumSink class
- Added MongoDB configuration to LinoleumConfig
- Fixed serialization for EvaluatedTrace: it should serialize as a [Scala POJO](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#pojos) instead of a using protobuf, because it is defined as a Scala case class, and not as a protocol buffers message.

## Ongoing Work
1. **Refactoring**:
   - Move the ad hoc code of Main.scala into a library

2. **Infrastructure**:
   - Setting up integration tests
   - Improving error recovery
   - Adding metrics collection
   - Integrating Maude-generated traces into test pipeline
   - Validating Flink checkpointing with synthetic load

3. **Tech debt**:
   - Redo protobuf generation to be able to upgrade the JDK

## Key Decisions Pending
- New trait for SscheckFormula suppliers
- Serialize LinoleumConfig to YAML or JSON, e.g. from a protobuf schema
