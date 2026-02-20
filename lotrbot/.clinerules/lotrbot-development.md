## Brief overview
This set of guidelines covers development practices for the LOTR bot project, a Python-based chatbot for querying knowledge about The Lord of the Rings and role-playing with characters.

## Development environment setup
- Use `uv` for Python package management instead of traditional pip
- Create virtual environment using `make clean venv deps`
- Set up environment file `~/.lotrbot.env` by copying from `lotrbot.env.example`
- Use Mistral AI models via Le Plataforme console for API access

## Build and testing workflow
- Run `make release` to execute linters, typechecking, and tests
- Use `make run` to start the agent with default environment file
- Integration tests require OTEL collector, Jaeger, and Kafka services running via `make -C ../linoleum compose/start`
- Generate HTML test reports in `.reports/` directory

## Code quality standards
- Use Ruff linter with automatic fixes (`make ruff`)
- Enforce type checking with MyPy (`make typechecking`)
- Follow Python 3.11.12 compatibility requirements
- Maintain clean build artifacts with `make clean`
- Quickly run all checks with `make release`

## Testing strategy
- Separate unit tests (`make test/unit`) and integration tests (`make test/integration`)
- Integration tests require specific environment setup with OTEL endpoints
- Use pytest for test execution with verbose output
- Generate self-contained HTML reports for test results

## Project structure
- Main source code located in `lotrbot/` directory
- Development utilities in `devenv/utils.sh`
- Tests organized in `tests/unit/` and `tests/integration/`
- Configuration files: `pyproject.toml`, `requirements.txt`, `dev-requirements.txt`

## Runtime configuration
- Support custom environment file paths via `LOTRBOT_ENV_FILE_PATH`
- Configure OpenTelemetry endpoint at `http://localhost:4318`
- Support adjustable insult likelihood via `INSULT_LIKELIHOOD` environment variable
- Image generation includes configurable sleep parameters

## Future development considerations
- Plan for misbehavior injection via tools or hooks
- Maintain compatibility with Strands agents HTTP protocol requirements
- Consider OpenTelemetry collector configuration for both gRPC and HTTP protocols