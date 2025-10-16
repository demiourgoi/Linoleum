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

Note, strands agents only supports HTTP protocol, and it ignores the env var [`OTEL_EXPORTER_OTLP_PROTOCOL`](https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/). For this reason the OTEL collector launched with Docker compose below, is [configured](https://opentelemetry.io/docs/collector/configuration/) to accept connections both with gRPC (port 4317) and HTTP (port 4318)

```bash
## Start fakes for OTEL collector, Jaeger and Kafka
### See Jaeger UI at http://localhost:16686
### See Kafka UI at  http://localhost:9090
make -C ../linoleum compose/start

# with default env file ~/.lotrbot.env
make run
# with custom env file
make run LOTRBOT_ENV_FILE_PATH=/my/path
```

Asking the agent to generate images:

```
> oh, I don't know what a Balrog looks like :(
I can help with that! Would you like me to generate an image of a Balrog for you?
---
> yes!
```

sometimes you gotta insist a bi

```
> I'm kind of a visual learner...

Tool #1: generate_image
Generating image for: 'A Balrog from the Lord of the Rings universe, depicted as a being of shadow and flame, with a whip and a sword of fire. It has a large, menacing form, often described as having the likeness of a shadowy figure with horns and claws. The Balrog is surrounded by an aura of darkness and fire, embodying its fiery and destructive nature.'
Here is an image of a Balrog from the Lord of the Rings universe.
---
```

## TODO

- Inject misbehaviour
  - Consider a tool
  - Consider using a [hook](https://strandsagents.com/latest/documentation/docs/user-guide/concepts/agents/hooks/?h=evol#overview) that randomly triggers bad behaviours, with adjustable likehood