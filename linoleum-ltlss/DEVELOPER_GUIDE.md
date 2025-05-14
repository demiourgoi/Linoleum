# Developer guide

## Setup

Prerequisites:

- Container runtime:
  - For windows: [Podman](https://podman.io/docs/installation)
  - For Ubuntu

```bash
sudo snap install docker-credential-pass --beta
# if the credential store it´s not initialized on `make compose/start`
# https://github.com/docker/docker-credential-helpers/issues/140
systemctl stop docker
rm ~/.docker/config.json
systemctl start docker
```

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

make run 2>&1 | tee run.log
```

and then:
 
- See Jaeger UI at http://localhost:16686
- See Kafka UI at  http://localhost:9090

## VsCode

I was able to debug on VsCode with [Gradle support for Metals](https://scalameta.org/metals/docs/build-tools/gradle/).  
Had to `delete app/src/main/java/io/jaegertracing/api_v3/QueryServiceGrpc.java` as Metals was failing to find the annotation after we commented the dependency `implementation "javax.annotation:javax.annotation-api:1.3.2"`: that's fine as we don't call Jaeger directly anymore

- The build currently only works for JDK8, for JDK 17 or 19 serialization fails with `java.lang.reflect.InaccessibleObjectException` in `com.twitter.chill.java.ArraysAsListSerializer` at `org.apache.flink.api.java.typeutils.runtime.kryo.FlinkChillPackageRegistrar.registerSerializers`. This is a problem known on https://issues.apache.org/jira/browse/FLINK-33161 with a workaround of settings some runtime flag. Note `make run` works because gradle is setup with `javaVersion = '1.8'`, that is the version used by the Flink project template. For now I'm configuring metals to use JDK 8 (for Windows get it from https://learn.microsoft.com/es-es/java/openjdk/download#openjdk-8), adding it to `.vscode\settings.json` (setting `"metals.javaHome": "C:\\Users\\...`, note the usage of `\\` for path components).
- Run the VsCode command `Metals: Import build` to setup the build, and use the launch configs on `.vscode\launch.json`. 
- Remember to use the same JVM options as in `app\build.gradle` for the launch configs.
- Flink web UI at http://localhost:8081/#/overview only works from `make run`, not from VsCode. This is probably an issue with Windows.

## How to build the code

Use make to build the code, that configured using gradle.

```bash
make build
make release
```

## Troubleshooting 

### Podman containers fail to start

If you get an error with podman retry restarting the VM with `podman machine stop && podman machine start`.

