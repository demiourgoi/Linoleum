SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

# Only publish build scan when explicitly asking, e.g. on CI
EXTRA_BUILD_ARGS ?= --no-scan
GRADLE := $(ROOT_DIR)/gradlew $(EXTRA_BUILD_ARGS)

default: help

.PHONY: run help build clean release clean/build jaeger/container/logs mongodb/container/bash compose/start compose/stop

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

run: ### run app with example flags
	$(GRADLE) run

