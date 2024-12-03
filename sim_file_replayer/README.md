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

`./gradlew run` takes its parameters from the following environment variables:

- SIM_FILE_PATH: path to the __Sim file___ to replay. This file is a JSON lines (https://jsonlines.org/) with a SimSpan per line serialzied as a JSON object.
  - `./gradlew build` generates an example Sim file at `app/build/simFiles/traces1.jsonl`
- REPLAY_TIMEOUT: simulation timeout in seconds.
  - Optional: 10 by default
- OTEL_EXPORTER_ENV_FILE: path of a bash file that exports env vars OTEL_EXPORTER_OTLP_PROTOCOL, OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_HEADERS that define URL and credentials to connect to an OTLP endpoint. See https://opentelemetry.io/docs/specs/otel/protocol/exporter/ for docs.
    - Optional: by default it uses `${HOME})/.otel/otel_exporter.env`
- MAX_THREAD_POOL_SIZE: number of threads to use for the simulation, should be higher or equal than the number of concurrent spans
  - Optional: 5000 by default

Gradle build is configured in `app/build.gradle.kts` and `settings.gradle.kts`

## Design

This commands sends data directly to an OTEL tracing endpoint, and does not rely on any variant the OTEL collector. This is because this is a development and simulation tool, not intended for production usage, so a simpler setup is more convenient (at least until we find a limitation on this approach).
