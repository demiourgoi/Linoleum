#!/usr/bin/env bash

set -e -u

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# See DEVELOPER_GUIDE.md for setting this up
PYTHON="${SCRIPT_DIR}/.venv/bin/python"
NUM_FILES=${1:-1}
OUT_DIR=${2:-json_tmp}

echo "Using PYTHON=${PYTHON}, NUM_FILES=${NUM_FILES}, OUT_DIR=${OUT_DIR}"
mkdir -p "${OUT_DIR}"

echo "Starting generation"
"${PYTHON}" "${SCRIPT_DIR}/generate.py" "${NUM_FILES}" "${OUT_DIR}/trace"

echo "Generation complete with success"
