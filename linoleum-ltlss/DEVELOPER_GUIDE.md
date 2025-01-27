# Developer guide

## How to build the code

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
