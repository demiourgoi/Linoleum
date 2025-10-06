SHELL := /bin/bash

.SILENT:

UNAME := $(shell uname)
ROOT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

PYTHON_VERSION := 3.11.12

MAIN_SRC := $(ROOT_DIR)/lotrbot
DEV_UTILS := $(ROOT_DIR)/devenv/utils.sh
VENV_DIR := $(ROOT_DIR)/.venv

PYTHON := $(VENV_DIR)/bin/python
PIP := $(VENV_DIR)/bin/pip
PODMAN_COMPOSE := docker-compose
PODMAN := docker

default: help

# https://news.ycombinator.com/item?id=11939200
.PHONY: help
help:	### list main targets

ifeq ($(UNAME), Linux)
	@grep -P '^[a-zA-Z_-_/]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
else
	@# this is not tested, but prepared in advance for you, Mac drivers
	@awk -F ':.*###' '$$0 ~ FS {printf "%15s%s\n", $$1 ":", $$2}' \
		$(MAKEFILE_LIST) | grep -v '@awk' | sort
endif

.PHONY: run
run: export LOTRBOT_ENV_FILE_PATH=$(shell echo $${LOTRBOT_ENV_FILE_PATH:-$(HOME)/.lotrbot.env})
run: export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
run:	### Run the agent
	$(PYTHON) -u $(MAIN_SRC)/main.py

.PHONY: venv
venv: 	### create virtual env
	echo
	source $(DEV_UTILS) && create_venv $(shell echo $${PYTHON_VERSION:-$(PYTHON_VERSION)}) $(VENV_DIR)
	$(PIP) install --upgrade pip

.PHONY: deps
deps: deps/prod deps/dev	### install dependencies

.PHONY: clean
clean: clean/typechecking 	### remove any compiled artifacts
	rm -rf $(VENV_DIR)

.PHONY: deps/prod
deps/prod:
	echo
	echo "Installing production dependencies ..."
	$(PIP) install -r $(ROOT_DIR)/requirements.txt


.PHONY: deps/dev
deps/dev:
	echo
	echo "Installing development dependencies ..."
	$(PIP) install -r $(ROOT_DIR)/dev-requirements.txt


.PHONY: release
release: checks	### run all checks and package for relase

.PHONY: checks
checks: lint test	### run all checks (linters, tests, etc)
checks:
	echo
	echo "All checks passing"

.PHONY: lint
lint: ruff typechecking

.PHONY: ruff
ruff:	### run ruff linter
	echo
	echo "Running ruff linter ..."
	$(PYTHON) -m ruff check $(MAIN_SRC) --fix tests


.PHONY: typechecking
typechecking:	### Check types
	echo
	echo "Running type checking ..."
	cd $(ROOT_DIR) && $(PYTHON) -m mypy --config-file $(ROOT_DIR)/pyproject.toml .

.PHONY: clean/typechecking
clean/typechecking:
	rm -rf $(MAIN_SRC)/.mypy_cache

.PHONY: test
test:
	echo
	echo "Running tests ..."
	echo "No tests for now"
