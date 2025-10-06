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
        eval "$(pyenv init -)"
        pyenv shell ${PYTHON_VERSION} || pyenv install ${PYTHON_VERSION}
        pyenv shell ${PYTHON_VERSION}
    fi

    python -m venv ${VENV_DIR}
}