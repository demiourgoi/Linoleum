# Developer guide

## Setup

Prerequisites:

- [SBT](https://www.scala-sbt.org/1.x/docs/Setup.html)
- [Podman](https://podman.io/docs/installation)
- `make`

## Local fakes

Use the Makefile to launch the local service targets

```bash
# see all targets
make

# launch a local jaeger service without auth
make jaeger/podman/start

# delete all containers
make clean
```

## How to build the code

Use SBT to build the code.

```bash
# Launch SBT shell
sbt

# list targets
tasks
# reload sbt config
reload

clean
compile
test
run

# run linter: see CI target on .github\workflows\sscheck_core.yml
scalafixEnable
scalafixAll --check

help
exit

# non interactive: slower
sbt -no-colors compile
```

## Troubleshooting 

### Podman containers fail to start

If you get an error with podman retry restarting the VM with `podman machine stop && podman machine start`.

