#!/usr/bin/env bash

function create_venv {
    # Check if the virtual env exists, and if not then ensure the required python version is
    # installed with pyenv, and create the virtual env.  
    # If ${PYTHON_VERSION} is "local" then do not perform the python pyenv check, and instead
    # use the `python` command available in PATH to create the virtual env.
    PYTHON_VERSION=${1}
    VENV_DIR=${2}
    echo "Creating virtual environment for PYTHON_VERSION=${PYTHON_VERSION}, VENV_DIR=${VENV_DIR}"
    ls ${VENV_DIR} &> /dev/null && return 0

    if [ "$PYTHON_VERSION" != "local" ]; then
       uv python find ${PYTHON_VERSION} || uv python install ${PYTHON_VERSION}
    fi

    # this does not install pip in the venv. We can fully migrate to uv but later on
    # uv venv --python ${PYTHON_VERSION}
    $(uv python find ${PYTHON_VERSION}) -m venv ${VENV_DIR}
}