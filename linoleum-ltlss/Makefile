SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

default: help

.PHONY: run help build clean release build/clean jaeger/container/logs mongodb/container/bash compose/start compose/stop

# https://news.ycombinator.com/item?id=11939200
help:	### list main targets
ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
else
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort
endif

####################
# Code build
####################
release: clean jaeger/container/start build	### run all release checks
	echo "PASS all release checks"

clean: build/clean compose/stop ### cleanup everything
	$(GRADLE) clean
	rm -rf $(ROOT_DIR)/bin

build/clean:	### cleanup the build
	$(GRADLE) clean
	rm -rf $(ROOT_DIR)/bin

# Run with `EXTRA_BUILD_ARGS=--scan` to send a gradle build scan, e.g.
# `make release EXTRA_BUILD_ARGS=--scan`
# https://docs.gradle.org/current/userguide/build_scans.html
build:	### build app (this also runs unit tests)
	$(GRADLE) build
	echo "See app jar at $(ROOT_DIR)/app/build/libs/*.jar"

run: ### run app with example flags
	$(GRADLE) run


####################
# Local fakes
####################

compose/start: compose/stop   ### launch all dev containers using the compose file
	podman compose --file $(ROOT_DIR)/devenv/compose.yaml up --detach
	echo "See Jaeger UI at http://localhost:16686"
 
compose/stop:  ### stop amnd delete all dev containers using the compose file
	podman compose --file $(ROOT_DIR)/devenv/compose.yaml down

JAEGER_CONTAINER ?= jaeger-local-all-in-one
.PHONY: jaeger/container/logs
jaeger/container/logs:  ### Show the logs of the test Jaeger container
	podman container logs $(JAEGER_CONTAINER)

OTEL_COLLECTOR_CONTAINER ?= otel-collector-local
otel-collector/container/logs:  ### Show the logs of the test OTEL collector container
	podman container logs $(OTEL_COLLECTOR_CONTAINER)

KAFKA_CONTAINER ?= kafka-local
kafka/container/logs:  ### Show the logs of the test kafka container
	podman container logs $(KAFKA_CONTAINER)

kafka/container/bash:  ### Run a bash shell in the kafka container
	echo
	echo 'Get cluster id: /opt/kafka/bin/kafka-cluster.sh cluster-id --bootstrap-server :9092'
	echo 'Publish to demo topic: /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server :9092 --topic demo'
	echo 'Consume demo topic: /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic demo --from-beginning'
	echo
	podman exec -it $(KAFKA_CONTAINER) bash

MONGODB_CONTAINER ?= mongodb-community-server-local
mongodb/container/bash:  ### Run a bash shell in the MongoDB container
	echo 'Use `mongosh --port 2017` or just `mongosh` to open a Mongo shell'
	podman exec -it $(MONGODB_CONTAINER) bash
