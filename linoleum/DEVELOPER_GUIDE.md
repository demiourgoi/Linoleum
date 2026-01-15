# Developer guide

## Setup

Prerequisites:

- Container runtime: [Podman](https://podman.io/docs/installation)

```bash
podman machine init
podman machine start
```

- `make`
- Optionally [`scala-cli`](https://scala-cli.virtuslab.org/install) is useful for trying out stuff quickly

## Local fakes

Use the Makefile to launch the local service targets

```bash
# see all targets
make

# launch local services without auth
# this resets the previous state of all services
make compose/start

# delete all containers
make compose/stop
```

and then:
 
- See Jaeger UI at http://localhost:16686
- See Kafka UI at  http://localhost:9090
- Install and launch [MongoDB Compass UI](https://www.mongodb.com/try/download/compass), connecting to the URI `mongodb://localhost:27017`.

Note on a remove SSH session with VsCode this works because VsCode auto forwards all the corresponding ports, check the "Ports" section of VsCode to debug potentially missing ports

## Run a simple simulation


```bash
# 1. Start the local fake dependencies
make compose/start

# 2. Generate some traces
## Trigger a canned interaction with lotrbot 
make -C ../lotrbot run/simple-canned-chat
### Optionally control agent behaviour parameters: 
#### this forces a fail of MaudeLotrImageGenSafety as it checks a 1 second limit in the image generation time
make -C ../lotrbot run/simple-canned-chat IMAGE_GEN_MIN_SLEEP_SECS=0.6 IMAGE_GEN_MAX_SLEEP_SECS=0.7 INSULT_LIKELIHOOD=0
#### this forces a pass of MaudeLotrImageGenSafety
make -C ../lotrbot run/simple-canned-chat IMAGE_GEN_MIN_SLEEP_SECS=0.1 IMAGE_GEN_MAX_SLEEP_SECS=0.2 INSULT_LIKELIHOOD=0

## Alternatively, generate and replay some traces
cd ../maude
rm -rf json_tmp &&  ./generate.sh 10 json_tmp
cd ../sim_file_replayer && make run SIM_FILE_DIR_PATH=$(pwd)/../maude/json_tmp

# 3. Process the traces 
## NOTE: this will fail if the source Kafka topic it's not created. The OTEL collector
##       creates the topic on the first replay
## NOTE: for small trace batches (e.g. 10) we have to run the replayer twice to
##       get a second Kafka message with the batch of topic, so Linoleum actually
##       evaluates the first batch. This could be improved tunning the Flink job parameters
cd ../linoleum-ltlss-examples
### Maude image generation safety example
make clean run EXAMPLE=MaudeLotrImageGenSafety 2>&1 | tee run.log
### Sscheck basic liveness example
make clean run EXAMPLE=SscheckBasicLiveness 2>&1 | tee run.log

# 4. To get all spans found in the input in Maude format, one span per line
find app/maude_terms -type f -exec cat {} + > maude_terms.maudes
## Check the logs
grep EvaluatedTrace run.log | grep False
grep "rewritten to current soup" run.log
tail -f run.log | grep "rewritten to current soup"
```

### Simple local benchmarking

A _crude_ local benchmarking can be run as follows. 

The sim file replayer [doesn't scale well](https://github.com/demiourgoi/Linoleum/issues/7), so we cannot replay more than 100 sim files at once. So here we replay 100 sim files with 12 spans each 10 times, and then launch Linoleum, that starts from the start of the Kafka topic and catches up. Note after a new `make compose/start` the Kafka topic for the spans it's missing, until the replayer sends some traces and the OTEL collector creates it, only then we can launch Linoleum ---otherwise the Flink job will fail due to an error connecting to the Kafka source. 


```bash
# generate 100 sim files
cd ../maude
rm -rf json_tmp &&  ./generate.sh 100 json_tmp_100

# replay the 100 sim files 10 times
cd ../sim_file_replayer
for i in $(seq 10)
do
  echo "Replaying file $i"
  MAX_THREAD_POOL_SIZE=10000 SIM_FILE_DIR_PATH=$(pwd)/../../../../maude/json_tmp_100 ./app/bin/app 2>&1 > "replay_$i.log" 
done
## ensure there are no replay errors
grep xcept replay_*

make run 2>&1 | tee run_1000.log
```

Note this only processes 900 traces due to the windowing configuration we have in Flink.  
With Flink local mode on an AMD Ryzen Embedded V1605B 3.6 GHz with 4 cores and 16 GB RAM, Linoleum processes 900 traces in 5 seconds (substracting the Flink job setup time).

```bash
09:50:17,343 WARN  es.ucm.fdi.demiourgoi.linoleum.ltlss.Main$                   [] - Starting program for formula LinoleumFormula(Luego basic liveness,es.ucm.fdi.demiourgoi.linoleum.ltlss.Main$HelloFormula@1139b2f3)
09:50:17,431 WARN  es.ucm.fdi.demiourgoi.linoleum.ltlss.source.LinoleumSrc$     [] - Open Flink web UI at http://localhost:8081/#/overview
09:50:23,382 DEBUG es.ucm.fdi.demiourgoi.linoleum.ltlss.source.ExportTraceServiceRequestProtoDeserializer$ [] - Parsed request with 1 spans
...
09:50:28,760 INFO  es.ucm.fdi.demiourgoi.linoleum.ltlss.Main$                   [] - Writing evaluated trace EvaluatedTrace(1a164375b7463f1e8ddfe4a55e01cb5d,1748677780405407993,Luego basic liveness,True) to MongoDB
```

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

## Publish to Maven Central

This repo uses mostly [semantic versioning](https://semver.org/), but we sometimes add the suffix "-SNAPSHOT" for development versions.  
We publish to https://central.sonatype.com/artifact/io.github.demiourgoi/linoleum_2.13.   
See one time setup instructions in our shared Google drive doc. __NOTE__ the file `config.toml` has to be copied to `~/.jreleaser/config.toml`.

Maven Central repos do not allow republish of the same version of a library. This is to avoid depending on a moving target, that corresponds to different code depending on the day. There is mechanism to use `${version}-SNAPSHOT` versions for development, but I have not been able to make it work, and it is not a great solution anyway for that reason.  
So the __publishing process__ is as follows:

- We use version `${version}-SNAPSHOT` for development, using `make publish/local` to make the snapshot version available in the same workspace.
- When a version is ready for publish
  - Change version to "${version}" without "-SNAPSHOT" and publish the version as seen below. Consider a minor or major version increase as required.
  - Increase the patch version and add "-SNAPSHOT". Consider a minor or major version increase as required.
    - We are not using branches or tags for each versions, we can add that later if needed, but it's complexity with no value for now. We just use the `main` branch, with the _invariant_ that the version monotonically increases as we move towards the tip of the branch.

How to actually publish, after making sure the release works fine:

```bash
make publish/central
```

References

- https://central.sonatype.org/publish/publish-portal-gradle/#community-plugins
- https://jreleaser.org/guide/latest/quick-start/cli.html
- https://jreleaser.org/guide/latest/examples/maven/maven-central.html
  - https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html
  - https://jreleaser.org/guide/latest/examples/maven/staging-artifacts.html#_gradle


## Troubleshooting 

### Podman containers fail to start

If you get an error with podman retry restarting the VM with `podman machine stop && podman machine start`.

