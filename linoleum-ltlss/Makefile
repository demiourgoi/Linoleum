SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

default: help

.PHONY: run help build clean release build/clean jaeger/podman/logs jaeger/podman/start jaeger/podman/stop mongodb/podman/bash mongodb/podman/start mongodb/podman/stop

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

release: clean build	### run all release checks
	echo "PASS all release checks"

clean: build/clean jaeger/podman/stop mongodb/podman/stop	### cleanup everything
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

JAEGER_CONTAINER ?= jaeger-local-all-in-one

.PHONY: jaeger/podman/stop
jaeger/podman/stop:  ### Stop the test Jaeger container
	podman container stop -i $(JAEGER_CONTAINER) || true
	podman container rm -i $(JAEGER_CONTAINER) || true
	echo "Test Jaeger container stopped with success"

.PHONY: jaeger/podman/start
jaeger/podman/start: jaeger/podman/stop  ### Launch a test Jaeger all-in-one container
	# https://www.jaegertracing.io/docs/2.3/getting-started/#all-in-one
	# Jaeger UI on http://localhost:16686
	echo "Running local Jaeger, all previous state will be lost"
	podman container run -d --name $(JAEGER_CONTAINER) \
	  	-p 16686:16686 \
  		-p 4317:4317 \
		-p 4318:4318 \
  		-p 5778:5778 \
  		-p 9411:9411 \
  		-p 16685:16685 \
  		jaegertracing/jaeger:2.3.0
	echo
	echo "See Jaeger UI at http://localhost:16686"

.PHONY: jaeger/podman/logs
jaeger/podman/logs:  ### Show the logs of the test Jaeger container
	podman container logs $(JAEGER_CONTAINER)


MONGODB_CONTAINER ?= mongodb-community-server-local
# https://hub.docker.com/r/mongodb/mongodb-community-server/tags
# https://www.mongodb.com/resources/products/mongodb-version-history#mdbsevenzero
MONGODB_IMAGE ?= mongodb/mongodb-community-server:7.0.17-rc1-ubi9
# https://www.mongodb.com/docs/manual/tutorial/install-mongodb-community-with-docker/
mongodb/podman/start: mongodb/podman/stop  ### Launch a test MongoDB server container
	echo "Running local MongoDB, all previous state will be lost"
	podman run -d --name $(MONGODB_CONTAINER) \
    	-p 27017:27017 $(MONGODB_IMAGE)
	echo
	echo 'Run `make mongodb/podman/bash` to open mongosh'

.PHONY: mongodb/podman/stop
mongodb/podman/stop:  ### Stop the test MongoDB container
	echo "Stopping Test MongoDB container"
	podman container stop -i $(MONGODB_CONTAINER) || true
	podman container rm -i $(MONGODB_CONTAINER) || true
	echo "Test MongoDB container stopped with success"

.PHONY: mongodb/podman/bash
mongodb/podman/bash:  ### Run a bash shell in the MongoDB container
	echo 'Use `mongosh --port 2017` or just `mongosh` to open a Mongo shell'
	podman exec -it $(MONGODB_CONTAINER) bash
