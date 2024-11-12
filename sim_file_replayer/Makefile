SHELL := /bin/bash

.SILENT:

.DEFAULT_GOAL := release

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

release: clean build
	echo "PASS all release checks"

clean:	### cleanup the build
	$(GRADLE) clean

build:	### build app (this also runs unit tests)
	$(GRADLE) build
	echo "See test report at file://$(ROOT_DIR)/app/build/reports/tests/test/index.html"

OTEL_EXPORTER_ENV_FILE ?= $(shell echo $${HOME})/.otel/otel_exporter.env
# https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/
run: export OTEL_SERVICE_NAME=linoleum.tools.simreplayer:kotlin
run: export OTEL_LOG_LEVEL=debug
# Export traces to OLTP so they go to Tempo, but also to the console to
# see in stdout with format "INFO; span_name : trace_id span_id ..."
run: export OTEL_TRACES_EXPORTER=otlp,console
run: export OTEL_METRICS_EXPORTER=console
run: export OTEL_LOGS_EXPORTER=console
run:	### run app with example flags
	echo "Using OTEL_EXPORTER_ENV_FILE='$(OTEL_EXPORTER_ENV_FILE)'"
	source $(OTEL_EXPORTER_ENV_FILE) && $(GRADLE) run
