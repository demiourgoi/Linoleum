SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))


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


clean:	tempo-client/clean ### cleanup the build

# As in https://github.com/grafana/tempo/blob/main/Makefile
## https://github.com/open-telemetry/build-tools/blob/main/protobuf/README.md
DOCKER_PROTOBUF_IMAGE ?= otel/build-protobuf:0.23.0
VENDOR_PROTO_ROOT = vendor/proto
PROTOC = podman run --rm -v .:/workspace -w /workspace ${DOCKER_PROTOBUF_IMAGE} --proto_path=/workspace
PROTO_INCLUDES = -I$(VENDOR_PROTO_ROOT)/opentelemetry
PROTO_GEN = $(PROTOC) $(PROTO_INCLUDES) --java_out=$(2) $(1)
PROTO_GEN_WITH_VENDOR = $(PROTOC) $(PROTO_INCLUDES) -Ivendor -Ivendor/proto/gogo --java_out=$(2) $(1)

tempo-client/gen:	### generate protocol buffers client for Tempo
	echo 'Starting proto generation'
	$(call PROTO_GEN,vendor/proto/opentelemetry/common/v1/common.proto,./src/main/java/)
	$(call PROTO_GEN,vendor/proto/opentelemetry/resource/v1/resource.proto,./src/main/java/)
	$(call PROTO_GEN,vendor/proto/opentelemetry/trace/v1/trace.proto,./src/main/java/)
	$(call PROTO_GEN,vendor/proto/tempo/tempo.proto,./src/main/java/)
	$(call PROTO_GEN,vendor/proto/tempodb/v1/v1.proto,./src/main/java/)
	$(call PROTO_GEN,vendor/proto/gogo/gogoproto/gogo.proto,./src/main/java/)
	echo 'Protobuf generation as Java code completed with success'

tempo-client/clean:
	rm -rf src/main/java/io/opentelemetry
	rm -rf src/main/java/com/grafana
	rm -rf src/main/java/com/google
	echo 'Success cleaning up proto generated Java code'
