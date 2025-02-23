SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

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

release: clean build	### run all release checks
	echo "PASS all release checks"

clean:	### cleanup the build
	$(GRADLE) clean

# Run with `EXTRA_BUILD_ARGS=--scan` to send a gradle build scan, e.g.
# `make release EXTRA_BUILD_ARGS=--scan`
# https://docs.gradle.org/current/userguide/build_scans.html
build:	### build app (this also runs unit tests)
	$(GRADLE) build
	echo "See test report at file://$(ROOT_DIR)/app/build/reports/tests/test/index.html"

OTEL_EXPORTER_ENV_FILE ?= $(shell echo $${HOME})/.otel/jaeger_otel_exporter.env
# Example script args
DEFAULT_SIM_FILE_PATH=$(ROOT_DIR)/app/build/simFiles/traces1.jsonl
run: export SIM_FILE_PATH=$(shell echo $${SIM_FILE_PATH:-$(DEFAULT_SIM_FILE_PATH)})
# Make OTEL log in English
# https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html
run: export JAVA_TOOL_OPTIONS=-Duser.language=en
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
