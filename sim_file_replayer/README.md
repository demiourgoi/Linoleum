# Simulation file replayer

## Development 

Basic gradle targets

```bash
# This leaves in app/build/distributions/ we have .zip and .tar distributions of the app, 
# with wrapper scrips to launch it
./gradlew build

./gradlew test

# Runs using as entry point the `mainClass` defined app/build.gradle.kts
# Expects a bash file with path defined by OTEL_EXPORTER_ENV_FILE (see Makefile for default value)
# exporting env vars OTEL_EXPORTER_OTLP_PROTOCOL, OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_HEADERS
# that define endpoint and credentials to connect to an OTLP endpoint.
# See https://opentelemetry.io/docs/specs/otel/protocol/exporter/ for docs.
./gradlew run

./gradlew clean
```

Gradle build is configured in `app/build.gradle.kts` and `settings.gradle.kts`

## Design

This commands sends data directly to an OTEL tracing endpoint, and does not rely on any variant the OTEL collector. This is because this is a development and simulation tool, not intended for production usage, so a simpler setup is more convenient (at least until we find a limitation on this approach).
