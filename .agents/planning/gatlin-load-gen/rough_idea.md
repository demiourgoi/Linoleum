# Load generation

We already have a spansProcessedCounter implemented on linoleum/lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala to emit processing speed metrics, that me measure with `sum by (job_name) (rate(flink_taskmanager_job_task_operator_linoleum_evaluator_linoleum_spans_processed_total[1m]))` in Prometheus

Now we need to generate traffic into the kafka container we launch in linoleum/devenv/compose.yaml

- See linoleum/DEVELOPER_GUIDE.md for how we run the Linoleum jobs 
- See linoleum/.cline/skills/linoleum-codebase/SKILL.md and linoleum/docs/design.md for a bit of info on this code base
- See the kafka config on linoleum-ltlss-examples/LocalLinoleumConfig.yaml
- See representative spans at linoleum-ltlss-examples/maude_terms.maudes. See logMaudeTerms at linoleum/lib/src/main/scala/io/github/demiourgoi/linoleum/SpanStreamEvaluator.scala for how those messags are logged

I want you to use https://github.com/Tinkoff/gatling-kafka-plugin to add a main to linoleum generate traffic for kafka. 
I want the typical traffic ramp shape: start ramping up to N messages and stay in that plateau, and then to M and stay and so on. 
I want plateaus of 100, 500, 1000, 5000, 10000. 
I dont care about measuring performance, we have Prometheus metrics on the processing job for that. I just want to ramp up the traffic, and if possible emit a log everytime i reach a new plateau


---
TODO

- mas recursos single JVM: como de lejos llegamos
- Flink standalone cluster (multiple TaskManager JVMs)
- haz load test con menos picos mas bajos
- update readme
- read steps from YAML file


```sh
./gradlew --stop
```
