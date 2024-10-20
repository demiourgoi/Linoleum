# Linoleum

Linoleum is an experiment for using observability signals for runtime verification of distributed systems

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

  - [OTEL Java API & SDK](https://opentelemetry.io/docs/languages/java/). See [manual instrumentation examples](https://opentelemetry.io/docs/languages/java/api-components/)
- Kotlin
  - [Kotlin in Action, 2E](https://livebook.manning.com/book/kotlin-in-action-second-edition)
  - [Gradle for Kotlin](https://kotlinlang.org/docs/gradle.html)
  - [Coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  - [Klint](https://www.baeldung.com/kotlin/ktlint-code-formatting)