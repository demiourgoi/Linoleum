# Linoleum

[Linoleum](https://www.youtube.com/watch?v=d9ORimXBXLw) is an experiment for using observability signals for runtime verification of distributed systems

## Cline setup

Using Cline [multi-root workspace](https://docs.cline.bot/features/multiroot-workspace) support:

- Open `linoleum.code-workspace` in VsCode, that contains all relevant projects in this git repo
- Rules and workflows are in `linoleum`, that VsCode assigns as primary because it is the first one in alphabetic order

__Attribution__: Strands Agents SOPs copied from https://github.com/strands-agents/agent-sop, also with Apache 2.0 license

## References

- Tracing
  - What is a [trace](https://grafana.com/docs/tempo/latest/introduction/)

    > A __trace__ represents the whole journey of a request or an action as it moves through all the nodes of a distributed system, especially containerized applications or microservices architectures. 
    > Traces are composed of one or more spans. A __span__ is a unit of work within a trace that has a _start time_ relative to the beginning of the trace, a _duration_ and an _operation name_ for the unit of work. It usually has a reference to a _parent span_ (unless it is the first span in a trace). It may optionally include _key/value attributes_ that are relevant to the span itself, for example the HTTP method being used by the span, as well as other metadata such as sub-span events or _links_ to other spans.
    > By definition, __traces are never complete__. You can always push a new batch of spans, even if days have passed since the last one. When receiving a query requesting a stored trace, tracing backends (for example Tempo), find all the spans for that specific trace and collate them into a returned result. For that reason, issues can start to arise predominantly on retrieval of the trace data if you are creating traces that are extremely large in size.

  - [Resource](https://opentelemetry.io/docs/concepts/resources/)
  
    > A resource represents the entity producing telemetry as resource attributes. For example, a process producing telemetry that is running in a container on Kubernetes has a process name, a pod name, a namespace, and possibly a deployment name. All four of these attributes can be included in the resource.

- __Open telemetry (OTEL)__
  - OTEL API vs SDK: [What is the difference between the API and SDK?](https://github.com/open-telemetry/opentelemetry-rust/issues/1186) tells
    - The __OTEL API__ is the interface to instrument telemetry, what your user code uses the sends metrics, logs and traces.
    - The __OTEL SDK__ implements the API, actually sending the telemetry somewhere. The API has a no-op implementation that sends telemetry nowhere, the SDKs does other stuff like sampling and interacts with actual telemetry storages. This separation allows to swap one telemetry technology with another without reimplementing instrumentation. See [OpenTelemetry Client Generic Design](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/library-guidelines.md#opentelemetry-client-generic-design) for more
    - The __OTEL [Collector](https://opentelemetry.io/docs/collector/)__ is a server where the OTEL SDK sends the telemetry, instead of sending it to storage directly. The collector can perform some local processing like aggregation or sampling to reduce the load to the telemetry storage. A simple deployment runs the collector as a [sidecar](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/) of your services, but some complex applications like [tail sampling](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/tailsamplingprocessor) requires running processing all spans for each trace to the same collector, which leads to things like a collector k8s Service
      - [When to use a collector](https://opentelemetry.io/docs/collector/#when-to-use-a-collector) makes clear a collector is optional, but in general it is recommended to offload features to improve reliability and fault tolerance of the metric collection process like batching and retries, as well as advanced features like sampling.

        > For trying out and getting started with OpenTelemetry, sending your data directly to a backend is a great way to get value quickly. Also, in a development or small-scale environment you can get decent results without a collector.
        > However, in general we recommend using a collector alongside your service, since it allows your service to offload data quickly and the collector can take care of additional handling like retries, batching, encryption or even sensitive data filtering.

  - [__Context propagation__](https://opentelemetry.io/docs/languages/java/instrumentation/#context-propagation) refers to adding metadata to the different __telemetry signals__ (metrics, logs, traces, ...) to be able to correlate signal values of different times that describe the same event. 
    - Typically done as follows: a trace span may have links to its parent span and to other spans; structured logs may include a trace id in some field; a metric [exemplar](https://opentelemetry.io/docs/specs/otel/metrics/data-model/#exemplars) is a metric time series data point that includes a trace id in its metadata. 
    - Linking is useful for discovery, and it's the main way of locating relevant traces during an incident: after grepping logs to find a relevant error message we can jump to a trace to get more details; or e.g. after identifying the time when a metric started to change then we can jump to traces that were emitted during the same event (e.g. API request handling) where those metrics are emitted. 
    - Exemplars are not supported by all metrics systems and even Prometheus has them being a feature flag. Often not all traces are emitted, only a sample as traces are a zoom inside the system and emitting every trace could be too much info to handle. So an exemplar is an association between a metric time series data point and a set of representative traces, that could be used to figure out why that metric value was emitted at that moment.
    - OTEL context API tries to make it easy for devs to maintain this correlation context between different signals. The [__Baggage__](https://opentelemetry.io/docs/concepts/signals/baggage/) is a set of key-value pairs that is part of the context, and that is inherited between telemetry signals that have a causal chain (in particular parent and child spans), even across process and host boundaries.  

      > For example, imagine you have a clientId at the start of a request, but you’d like for that ID to be available on all spans in a trace, some metrics in another service, and some logs along the way. Because the trace may span multiple services, you need some way to propagate that data without copying the clientId across many places in your codebase.
      > By using Context Propagation to pass baggage across these services, the clientId is available to add to any additional spans, metrics, or logs. Additionally, instrumentations automatically propagate baggage for you.

    - [__Semantic conventions__](https://opentelemetry.io/docs/specs/semconv/) and standarized conventions on how telemetry should emitted, e.g. standard names for span attributes like `server.address` that should indicate the host name of the server serving a request. The context propagation API makes use of these conventions.
  - [OTEL Java API & SDK](https://opentelemetry.io/docs/languages/java/). 
    - [manual instrumentation examples](https://opentelemetry.io/docs/languages/java/api-components/)
    - [Record Telemetry with API](https://opentelemetry.io/docs/languages/java/api-components)
    - Not using the [Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) as that is used for zero-code instrumentation. 
    - [Java SDK Configuration](https://opentelemetry.io/docs/languages/java/configuration/)
    - [OpenTelemetry API Javadoc](https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/index.html)
    - [Log instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/#log-instrumentation): the log record builder provided by the OTEL's `Logger::logRecordBuilder` is NOT meant to be used directly by application code, but through some bridge to some existing logging API like Log4j / SLF4J / Logback / etc. See e.g. [How to Create a Log4J Log Appender](https://opentelemetry.io/docs/specs/otel/logs/supplementary-guidelines/)
    - [Environment Variable Specification](https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/)
    - [OpenTelemetry Protocol Exporter](https://opentelemetry.io/docs/specs/otel/protocol/exporter/)


