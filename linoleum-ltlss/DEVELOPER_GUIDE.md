# Developer guide

## Setup

Prerequisites:

- [Podman](https://podman.io/docs/installation)
- `make`

Also install `sscheck-core` in your local maven repo, see [linoleum-ltlss.yml](../.github/workflows/linoleum-ltlss.yml) for how this works on Github actions

## Local fakes

Use the Makefile to launch the local service targets

```bash
# see all targets
make

# launch a local jaeger service without auth
make compose/start

# delete all containers
make compose/stop
```

and then:
 
- See Jaeger UI at http://localhost:16686
- See Kafka UI at  http://localhost:9090

## How to build the code

Use make to build the code, that configured using gradle.

```bash
make build
make release
```

## Troubleshooting 

### Podman containers fail to start

If you get an error with podman retry restarting the VM with `podman machine stop && podman machine start`.

