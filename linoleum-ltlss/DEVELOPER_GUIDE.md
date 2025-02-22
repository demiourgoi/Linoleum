# Developer guide

## Setup

Prerequisites:

- [SBT](https://www.scala-sbt.org/1.x/docs/Setup.html)
- [Podman](https://podman.io/docs/installation)

## How to build the code

First of all render the gRPC models into Java code.

```bash
make clean tempo-client/gen
```

If you get an error with podman retry restarting the VM with `podman machine stop && podman machine start`.

Then use SBT to build the code.

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
