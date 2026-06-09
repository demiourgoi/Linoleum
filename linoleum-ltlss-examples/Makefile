SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

default: help

.PHONY: run help build clean release clean/build jaeger/container/logs mongodb/container/bash compose/start compose/stop flink/download flink/config flink/restore-config flink/start flink/stop flink/build flink/run

# https://news.ycombinator.com/item?id=11939200
help:	### list main targets
ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

PODMAN_COMPOSE := docker-compose
PODMAN := docker

else
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort

PODMAN_COMPOSE := podman compose
PODMAN := podman
endif

####################
# Code build
####################
release: build	### run all release checks
	echo "PASS all release checks"

clean: clean/build ### cleanup everything

clean/build:	### cleanup the build
	$(GRADLE) clean
	rm -rf $(ROOT_DIR)/bin
	rm -rf $(ROOT_DIR)/app/maude_terms

# Run with `EXTRA_BUILD_ARGS=--scan` to send a gradle build scan, e.g.
# `make release EXTRA_BUILD_ARGS=--scan`
# https://docs.gradle.org/current/userguide/build_scans.html
build:	### build app (this also runs unit tests)
	$(GRADLE) build
	echo "See app jar at $(ROOT_DIR)/app/build/libs/*.jar"

# For code base approach to main set `mainClass = "io.github.demiourgoi.linoleum.examples.Main"`
# on app/build.gradle
# Example: make run EXAMPLE=MaudeLotrImageGenSafety
# run: ### run app with example flags
# 	$(GRADLE) run --args "$${EXAMPLE}"

# For YAML based approach
# Increase JVM heap for the Flink mini-cluster via _JAVA_OPTIONS.
# Override with: make run EXAMPLE=... JAVA_HEAP=24g
JAVA_HEAP ?= 16g
run: ### run app with example flags
	export _JAVA_OPTIONS="-Xmx$(JAVA_HEAP)" && \
	$(GRADLE) run --args " $$(pwd)/LocalLinoleumConfig.yaml $$(pwd)/$${EXAMPLE}"

####################
# Flink standalone cluster for multi-JVM load testing
####################

# Path to your Flink distribution.
# Override with: make flink/start FLINK_HOME=/other/path
FLINK_HOME ?= $(HOME)/systems/flink/flink-1.20.4
FLINK_BIN := $(FLINK_HOME)/bin/flink
FLINK_CONF_BAK := $(FLINK_HOME)/conf.orig

# Default example for flink/run
EXAMPLE ?= MaudeLotrImageGenSafety.yaml

flink/download: ### download an alternative Flink distribution to .flink/
	@echo "Downloading Flink..."
	mkdir -p $(ROOT_DIR)/.flink
	cd $(ROOT_DIR)/.flink && \
		curl -L -O "https://dlcdn.apache.org/flink/flink-1.20.1/flink-1.20.1-bin-scala_2.13.tgz" && \
		tar -xzf flink-1.20.1-bin-scala_2.13.tgz && \
		rm flink-1.20.1-bin-scala_2.13.tgz
	@echo "Flink downloaded to $(ROOT_DIR)/.flink/flink-1.20.1"
	@echo "Use it with: make flink/start FLINK_HOME=$(ROOT_DIR)/.flink/flink-1.20.1"

flink/config: ### apply Linoleum cluster config to FLINK_HOME (backs up original)
	@test -d "$(FLINK_HOME)" || { echo "ERROR: FLINK_HOME=$(FLINK_HOME) not found. Set FLINK_HOME or run 'make flink/download'"; exit 1; }
	@if [ ! -d "$(FLINK_CONF_BAK)" ]; then \
		echo "Backing up original Flink config to $(FLINK_CONF_BAK)..."; \
		cp -r $(FLINK_HOME)/conf $(FLINK_CONF_BAK); \
	fi
	@echo "Applying Linoleum cluster configuration to $(FLINK_HOME)/conf..."
	cp $(ROOT_DIR)/flink-cluster/flink-conf.yaml $(FLINK_HOME)/conf/
	cp $(ROOT_DIR)/flink-cluster/workers $(FLINK_HOME)/conf/
	cp $(ROOT_DIR)/flink-cluster/masters $(FLINK_HOME)/conf/
	cp $(ROOT_DIR)/flink-cluster/log4j2.properties $(FLINK_HOME)/conf/

flink/restore-config: ### restore original Flink config from backup
	@if [ -d "$(FLINK_CONF_BAK)" ]; then \
		echo "Restoring original Flink config..."; \
		rm -rf $(FLINK_HOME)/conf; \
		cp -r $(FLINK_CONF_BAK) $(FLINK_HOME)/conf; \
		rm -rf $(FLINK_CONF_BAK); \
	else \
		echo "No config backup found at $(FLINK_CONF_BAK)"; \
	fi

flink/start: flink/config ### start the Flink standalone cluster (5 TMs, 3 GB each)
	@echo "Starting Flink standalone cluster at $(FLINK_HOME)..."
	$(FLINK_HOME)/bin/start-cluster.sh
	@echo ""
	@echo "Flink Web UI: http://localhost:8081"
	@echo "Run 'make flink/run' to submit the Linoleum job"

flink/stop: ### stop the Flink standalone cluster
	$(FLINK_HOME)/bin/stop-cluster.sh

flink/build: ### build the fat JAR for Flink standalone submission
	$(GRADLE) flinkJobJar
	@echo "Flink job JAR: $(ROOT_DIR)/app/build/libs/app-flink-job.jar"

flink/run: flink/build ### build and submit the Linoleum job to the standalone cluster
	$(FLINK_BIN) run \
		-c io.github.demiourgoi.linoleum.RunMaudeMonitor \
		$(ROOT_DIR)/app/build/libs/app-flink-job.jar \
		$(ROOT_DIR)/LocalLinoleumConfig.standalone.yaml \
		$(ROOT_DIR)/$(EXAMPLE)
