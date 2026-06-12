SHELL := /bin/bash

.SILENT:

ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))
LINOLEUM_PROTO_DIR := $(ROOT_DIR)/../linoleum/lib/src/main/java/io
GRADLE := $(ROOT_DIR)/gradlew --no-scan

default: help

.PHONY: run help build clean proto

help:   ### list main targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

proto:  ### copy protobuf Java sources from linoleum/lib
	rm -rf $(ROOT_DIR)/src/main/java/io
	mkdir -p $(ROOT_DIR)/src/main/java
	cp -r $(LINOLEUM_PROTO_DIR) $(ROOT_DIR)/src/main/java/io/

build: proto ### compile the project
	$(GRADLE) build

run: proto  ### launch the load generator against localhost:9092
	$(GRADLE) run

clean:  ### clean build artifacts
	$(GRADLE) clean
