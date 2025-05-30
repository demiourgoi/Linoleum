# Simulation file replayer

## One time setup

Install a JDK.

## Development

Basic gradle targets

```bash
# This leaves in app/build/distributions/ we have .zip and .tar distributions of the app, 
# with wrapper scrips to launch it
./gradlew build

./gradlew test

# Runs using as entry point the `mainClass` defined app/build.gradle.kts
./gradlew run

./gradlew clean
```

Gradle build is configured in `app/build.gradle.kts` and `settings.gradle.kts`

VsCode setup

- Install a JDK higher than 20, and locate if. For Ubuntu you can use `update-alternatives --list java`
- Add the following to .vscode/settings.json:

```json
{
    "kotlin.compiler.jvm.target": "20",
    "java.configuration.runtimes": [
        // adjust this to your JDK version
        {
           "name": "JavaSE-21",
           "path": "/usr/lib/jvm/java-21-openjdk-amd64"
        }
    ],
}
```

## How to run a simulation

`./gradlew run` __runs a simulation__, taking its _parameters_ from the following _environment variables:

- SIM_FILE_PATH: path to the __Sim file___ to replay. This file is a JSON lines (https://jsonlines.org/) with a SimSpan per line serialzied as a JSON object.
  - `./gradlew build` generates an example Sim file at `app/build/simFiles/traces1.jsonl`
- REPLAY_TIMEOUT: simulation timeout in seconds.
  - Optional: 10 by default
- OTEL_EXPORTER_ENV_FILE: path of a bash file that exports env vars OTEL_EXPORTER_OTLP_PROTOCOL, OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_HEADERS that define URL and credentials to connect to an OTLP endpoint. See https://opentelemetry.io/docs/specs/otel/protocol/exporter/ for docs.
    - Optional: by default it uses devenv/local_jaeger_otel_exporter.env that is configured for a [Jaeger](https://www.jaegertracing.io/) instance running as a local container. See [linoleum-ltlss developer guide](../linoleum-ltlss/DEVELOPER_GUIDE.md) for instructions to easily launch such container.
- MAX_THREAD_POOL_SIZE: number of threads to use for the simulation, should be higher or equal than the number of concurrent spans
  - Optional: 5000 by default

__Simulation precision__: with 500ms of granularity we already get a 1% error in the timing of the replayed spans. As a workaround we can scale the simulation to use longer times, while respecting the causal relationships between events, and their relative durations.  
Also, the replayer goes ahead for spans that are starting too late, scheduling them immediately instead of failing. This is to support spans to start just at the beginning of their parent trace or span. This introduces inaccuracies of milliseconds (1 digit). To cope with that, use a time scale large enough so those errors are negligible.  

## Design

This commands sends data directly to an OTEL tracing endpoint, and does not rely on any variant the OTEL collector. This is because this is a development and simulation tool, not intended for production usage, so a simpler setup is more convenient (at least until we find a limitation on this approach).

## References

- Kotlin
  - [Kotlin in Action, 2E](https://livebook.manning.com/book/kotlin-in-action-second-edition)
  - [Quick reference](https://kotlin-quick-reference.com/)
  - [Gradle for Kotlin](https://kotlinlang.org/docs/gradle.html)
  - [Coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  - [KDoc](https://kotlinlang.org/docs/kotlin-doc.html)
  - [Klint](https://www.baeldung.com/kotlin/ktlint-code-formatting)
  - Serialization
    - [High level guide](https://kotlinlang.org/docs/serialization.html#serialize-and-deserialize-json)
    - [Low level details](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md): the plugin mentioned on the high level guide is what generates serilizer implemenations for classes marked as `Serializable`
    - [High level details](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/basic-serialization.md#basics)
    - [ContextualSerializer](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-core/kotlinx.serialization/-contextual-serializer/): a mechanism for using custom serialization for specific fields and combine it with the serializer implemented by the plugin. E.g. when using a type defiend in another library.
  - [Properties](https://kotlinlang.org/docs/properties.html): "In Kotlin, a field is only used as a part of a property to hold its value in memory. __Fields cannot be declared directly__. However, when a property needs a _backing field_, Kotlin provides it automatically. This backing field can be referenced in the accessors using the `field` identifier". Nevertheless, a `private` class property (declared either as constructor params or in the constructor body) doesn't get public gettters and setters generated
  - Logging
    - [Idiomatic way of logging in Kotlin](https://stackoverflow.com/questions/34416869/idiomatic-way-of-logging-in-kotlin)
    - [java.util.logging](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html)
  - [Sequences](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/-sequence/): [analogous to Java 8 streams](https://www.baeldung.com/kotlin/java-8-stream-vs-kotlin), seem closer to Scala streams but dependencing on the implementation may only be iterated once.
  - Testing
    - [kotlin-test](https://kotlinlang.org/api/core/kotlin-test/), included in the standard library, has integration with standard Java test frameworks
    - [Kotest](https://kotest.io/) is an a test framework with PBT capabilities, an assertion library, and several testing styles including BDD and data driven testing
      - [Kotest IntelliJ plugin](https://kotest.io/docs/intellij/intellij-plugin.html)
        - Until https://github.com/kotest/kotest/issues/4261 is solved this only works when [disabling K2 Kotlin compiler mode on IntelliJ](https://blog.jetbrains.com/idea/2024/03/k2-kotlin-mode-alpha-in-intellij-idea/)
      - [Baeldung examples](https://github.com/Baeldung/kotlin-tutorials/tree/master/kotlin-kotest)
    - [jqwik](https://jqwik.net/) is a Property-Based Testing (PBT) for the JVM, targeting Kotlin among others