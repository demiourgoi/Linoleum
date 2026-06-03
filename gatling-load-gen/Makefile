SHELL := /bin/bash

.SILENT:

ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))
GRADLE := $(ROOT_DIR)/gradlew --no-scan

default: help

.PHONY: run help build clean

help:	### list main targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

build:	### compile the project
	$(GRADLE) build

run:	### launch the load generator against localhost:9092
	$(GRADLE) run

clean:	### clean build artifacts
	$(GRADLE) clean
