SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

GRADLE := $(ROOT_DIR)/gradlew

default: help

# https://news.ycombinator.com/item?id=11939200
.PHONY: help
help:	### list main targets
ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
else
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort
endif

clean:	### cleanup the build
	$(GRADLE) clean

build: export OTEL_SDK_DISABLED=true
build:	### build app (this also runs unit tests)
	$(GRADLE) build

# https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/
run: export OTEL_SERVICE_NAME=linoleum.tools.simreplayer:kotlin
run: export OTEL_LOG_LEVEL=debug
# TODO: set to use Grafana Cloud
run: export OTEL_TRACES_EXPORTER=console
run: export OTEL_METRICS_EXPORTER=console
run: export OTEL_LOGS_EXPORTER=console

run:	### run app with example flags
	$(GRADLE) run