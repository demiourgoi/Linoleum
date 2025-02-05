# opentelemetry-proto 

Copied from https://github.com/open-telemetry/opentelemetry-proto/tree/39339ef177218cc965b8cf863d761775ec668858 aka tag v1.3.0

Modified as in https://github.com/grafana/tempo/blob/main/Makefile target `gen-proto`

```bash
OTEL_PROTOS_DIR=Linoleum/linoleum-ltlss/vendor/proto/opentelemetry
SED_OPTS=''
find $OTEL_PROTOS_DIR -name "*.proto" | xargs -L 1 sed -i ${SED_OPTS} 's+ opentelemetry.proto+ tempopb+g'
find $OTEL_PROTOS_DIR -name "*.proto" | xargs -L 1 sed -i ${SED_OPTS} 's+go.opentelemetry.io/proto/otlp+github.com/grafana/tempo/pkg/tempopb+g'
find $OTEL_PROTOS_DIR -name "*.proto" | xargs -L 1 sed -i ${SED_OPTS} 's+import "opentelemetry/proto/+import "+g'
```