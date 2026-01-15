SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))
MAUDE_SRC := $(ROOT_DIR)/../maude
MAUDE_RES := $(ROOT_DIR)/lib/src/main/resources/maude

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

default: help

.PHONY: run help build clean release clean/build jaeger/container/logs mongodb/container/bash compose/start compose/stop

PODMAN_COMPOSE := podman-compose
PODMAN := podman

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
release: build publish/local	### run all release checks
	echo "PASS all release checks"

clean: clean/maude/copysrc clean/build compose/stop ### cleanup everything

clean/build:	### cleanup the build
	$(GRADLE) clean
	rm -rf $(ROOT_DIR)/bin

# Run with `EXTRA_BUILD_ARGS=--scan` to send a gradle build scan, e.g.
# `make release EXTRA_BUILD_ARGS=--scan`
# https://docs.gradle.org/current/userguide/build_scans.html
build: maude/copysrc	### build app (this also runs unit tests)
	mkdir -p target/specs2-reports/stats
	$(GRADLE) build
	echo "See lib jar at $(ROOT_DIR)/lib/build/libs/*.jar"


.PHONY: maude/copysrc
maude/copysrc: clean/maude/copysrc
	mkdir -p $(MAUDE_RES)/linoleum
	cp $(MAUDE_SRC)/linoleum/trace.maude $(MAUDE_RES)/linoleum


.PHONY: clean/maude/copysrc
clean/maude/copysrc:
	rm -rf $(MAUDE_RES)

publish/local: build	### publish this library to the local maven repo
	$(GRADLE) publishToMavenLocal
	echo "Library published to ~/.m2/repository/io/github/demiourgoi/linoleum_2.13"

# Stage first with gradle publish
# https://jreleaser.org/guide/latest/examples/maven/maven-central.html#_gradle
publish/central: clean/build build	### publish this library to Maven Central
	$(GRADLE) jreleaserConfig
	$(GRADLE) clean
	$(GRADLE) publish
	tree lib/build/staging-deploy/
	$(GRADLE) jreleaserDeploy

####################
# Local fakes
####################

compose/start: export UID=$(shell id -u)
compose/start: export GID=$(shell id -g)
compose/start: compose/stop   ### launch all dev containers using the compose file
	$(PODMAN_COMPOSE) --file $(ROOT_DIR)/devenv/compose.yaml up --detach
	echo "Using UID=${UID}, GID=${GID}"
	echo "See Jaeger UI at http://localhost:16686"
	echo "   - Note traces in Jaeger are different than in OTEL: attributes are tags events are logs"
	echo "See Kafka UI at  http://localhost:9090"
	echo "Use MongoDB Compass UI to connect to mongoDB at mongodb://localhost:27017"
 
compose/stop:  ### stop amnd delete all dev containers using the compose file
	$(PODMAN_COMPOSE) --file $(ROOT_DIR)/devenv/compose.yaml down --remove-orphans

JAEGER_CONTAINER ?= jaeger-local-all-in-one
.PHONY: jaeger/container/logs
jaeger/container/logs:  ### Show the logs of the test Jaeger container
	$(PODMAN) container logs $(JAEGER_CONTAINER)

OTEL_COLLECTOR_CONTAINER ?= otel-collector-local
otel-collector/container/logs:  ### Show the logs of the test OTEL collector container
	$(PODMAN) container logs $(OTEL_COLLECTOR_CONTAINER)

KAFKA_CONTAINER ?= kafka-local
kafka/container/logs:  ### Show the logs of the test kafka container
	$(PODMAN) container logs $(KAFKA_CONTAINER)

MONGODB_CONTAINER ?= mongodb-community-server-local
mongodb/container/bash:  ### Run a bash shell in the MongoDB server container
	echo 'Use `mongosh --port 27017` or just `mongosh` to open a Mongo shell'
	$(PODMAN) exec -it $(MONGODB_CONTAINER) bash

#  netstat -tulpn | grep 27017
# NOTE: should be the same as in "mongodb" service on devenv/compose.yaml
# NOTE: using host networking to be able to connect to the port exposed to the 
# host on the compose file
MONGO_SERVER_IMG := mongodb/mongodb-community-server:7.0.17-rc1-ubi9
mongoc/container/bash:  ### Run a bash shell a the MongoDB client container
	echo 'Use `mongosh --port 27017` to open a Mongo shell'
	echo 'See mounted scripts on /usr/share/mongosh'
	$(PODMAN) container run --rm -it \
	--network=host \
	-v $(ROOT_DIR)/devenv/mongosh:/usr/share/mongosh \
	 $(MONGO_SERVER_IMG) bash

apidocs:  ### Generate all API documentation
	$(GRADLE) javadoc
	$(GRADLE) scaladoc
	echo "See generated documentation at $(ROOT_DIR)/lib/build/docs"
