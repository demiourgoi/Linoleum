# Developer guide

## Python setup

Install python with [pyenv](https://github.com/pyenv/pyenv) 
Setup a virtual env for python 3.11

```bash
# check installed pythons
pyenv shims
# install python 3.11 if needed
pyenv install 3.11

pyenv shell 3.11
python -m venv .venv

# activate new virtual env and install dependencies
source .venv/bin/activate
pip install -r requirements.txt
```
