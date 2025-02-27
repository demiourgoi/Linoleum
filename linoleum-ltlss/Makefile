SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))


default: help

# https://news.ycombinator.com/item?id=11939200
.PHONY: help
help:	### List main targets
ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
else
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort
endif

.PHONY: clean
clean:	jaeger/podman/stop ### Cleanup the build

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