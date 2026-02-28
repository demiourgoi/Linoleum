# Linoleum Codebase Knowledge Base Index

## Purpose
This knowledge base provides comprehensive documentation for the Linoleum runtime verification system. It is designed to help AI assistants understand the codebase structure, design patterns, and implementation details to effectively analyze code, make changes, and propose technical designs.

## How to Use This Documentation

### For AI Assistants
1. **Start with this index** to understand what information is available
2. **Consult specific files** based on your task:
   - For architectural questions: `architecture.md`
   - For component details: `components.md`
   - For API interfaces: `interfaces.md`
   - For data structures: `data_models.md`
   - For workflows: `workflows.md`
   - For dependencies: `dependencies.md`
3. **Reference `codebase_info.md`** for high-level overview and technology stack
4. **Check `review_notes.md`** for known gaps and limitations

### File Descriptions

| File | Purpose | Key Contents |
|------|---------|--------------|
| `codebase_info.md` | High-level overview | Project structure, technology stack, key interfaces, design principles |
| `architecture.md` | System architecture | Data flow, component interactions, design patterns, Mermaid diagrams |
| `components.md` | Component details | Detailed descriptions of each major component, responsibilities, relationships |
| `interfaces.md` | API interfaces | Type classes, traits, configuration classes, public APIs |
| `data_models.md` | Data structures | SpanInfo, LinoleumEvent, EvaluatedSpans, Maude terms, BSON documents |
| `workflows.md` | Key processes | Span ingestion, event processing, property evaluation, result persistence |
| `dependencies.md` | External dependencies | Flink, Kafka, MongoDB, Maude, OpenTelemetry, build tools |
| `review_notes.md` | Documentation review | Consistency checks, completeness gaps, improvement recommendations |

## Quick Reference Guide

### Common Tasks and Where to Find Information

#### Understanding the Data Flow
1. **Source**: Kafka → `LinoleumSrc` → `SpanInfo` objects
2. **Processing**: `SpanStreamEvaluator` → `LinoleumEvent` conversion → windowing
3. **Evaluation**: `Property[P]` type class → `LinoleumFormula` or `MaudeMonitor`
4. **Sink**: `EvaluatedSpans` → `LinoleumSink` → MongoDB

#### Adding New Property Types
1. Implement `Property[P]` type class (see `interfaces.md`)
2. Add to `PropertyInstances` companion object
3. Create factory methods in `Linoleum` object
4. Update configuration if needed (see `components.md`)

#### Modifying Event Processing
1. Review `SpanStreamEvaluator` in `components.md`
2. Understand `LinoleumEvent` hierarchy in `data_models.md`
3. Check windowing logic in `workflows.md`

#### Working with Maude Integration
1. See Maude term representation in `data_models.md`
2. Review `MaudeMonitor` configuration in `components.md`
3. Check state management in `architecture.md`

## Key Design Patterns

### Type Class Pattern
The `Property[P]` trait enables extensible property evaluation. See `interfaces.md` for details.

### Session Window Processing
Uses Flink's `EventTimeSessionWindows` for trace grouping. See `workflows.md` for windowing logic.

### Formal Methods Integration
- **LTLss**: Stateless formula evaluation via `LinoleumFormula`
- **Maude**: Stateful program evaluation via `MaudeMonitor`

### Configuration Management
Hierarchical configuration with `LinoleumConfig`, `SourceConfig`, `SinkConfig`. See `components.md`.

## Development Guidelines

### Testing
- Unit tests in `lib/src/test/scala/io/github/demiourgoi/linoleum/`
- Integration tests with embedded Kafka/MongoDB
- Property tests for evaluation logic

### Building and Running
- Use `make` commands (see `DEVELOPER_GUIDE.md`)
- Gradle build system with Scala 2.13
- Flink 1.20.1 with Kafka/MongoDB connectors

### Code Organization
- Main package: `io.github.demiourgoi.linoleum`
- Subpackages: `config`, `source`, `sink`, `evaluator`, `formulas`, `maude`, `messages`, `utils`
- Generated Java code from protobufs in `messages/` package

## Related Documentation

- `SKILL.md`: Agent skill documentation (consolidated overview)
- `DEVELOPER_GUIDE.md`: Setup and development instructions
- `docs/design.md`: Technical design document
- `README.md`: Project overview

## Metadata

**Last Updated**: Generated from codebase analysis  
**Codebase Version**: Based on commit `f2aecf8c2512dabebe14bdec1b334edb4928acfc`  
**Primary Language**: Scala 2.13  
**Build System**: Gradle  
**Key Dependencies**: Apache Flink, Kafka, MongoDB, Maude, OpenTelemetry