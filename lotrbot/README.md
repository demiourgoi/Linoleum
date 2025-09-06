# LOTR bot

A chatbot for querying knowledge about The Lord of the Rings, and role-playing  with characters.

## Development

### One time setup


Install prerequisites:

- [pyenv](https://github.com/pyenv/pyenv)
- `make`
- If you are in windows consider installing a bash shell like [git bash](https://gitforwindows.org/) or [MinGW](https://sourceforge.net/projects/mingw/)


Setup the workspace

```bash
# Create a [virtual env](https://docs.python.org/3/library/venv.html) and install dependencies:
make venv deps
# Run linters, typechecking, and tests
make release
# List all make target
make
```

Use the virtual env with command line or IDEs like VsCode

Setup an env file `~/.lotrbot.env`, or at any path you want. by copying [`lotrbot.env.example`](./lotrbot.env.example) and adding the required keys, as indicated in the comments on that file.  
To get access to [Mistral free models](https://docs.mistral.ai/) you can log into [Le Plataforme](https://console.mistral.ai/home) and get an API key

## How to run the agent

```bash
# with default env file ~/.lotrbot.env
make run
# with custom env file
make run LOTRBOT_ENV_FILE_PATH=/my/path
```