import os
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from strands import Agent
from strands.models.model import Model
from strands.models.mistral import MistralModel
from strands.telemetry import StrandsTelemetry

_root_path = Path(__file__).resolve().parent.parent


def _get_env_file_path() -> str:
    env_file_path = os.environ.get('LOTRBOT_ENV_FILE_PATH')
    if env_file_path is None:
        raise RuntimeError(f"""Please define LOTRBOT_ENV_FILE_PATH to get access to required config.
See {_root_path / 'README.md'} for instructions.""")
    print(f"Using LOTRBOT_ENV_FILE_PATH='{env_file_path}'")
    return env_file_path


class Settings(BaseSettings):
    # https://docs.pydantic.dev/latest/concepts/pydantic_setting
    model_config = SettingsConfigDict(
        env_file=_get_env_file_path(),
        env_file_encoding='utf-8',
        extra='ignore'
    )

    mistral_api_key: str = Field(..., repr=False)


def setup_tracing(enable_console_exporter: bool = False):
    # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#enabling-tracing
    # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#example-end-to-end-tracing
    strands_telemetry = StrandsTelemetry()
    strands_telemetry.setup_otlp_exporter()     # Send traces to OTLP endpoint
    if enable_console_exporter:
        strands_telemetry.setup_console_exporter()  # Print traces to console


def create_mistral_model(settings: Settings) -> Model:
    # https://docs.mistral.ai/#free-models
    return MistralModel(
        api_key=settings.mistral_api_key,
        model_id="mistral-small-latest",
    )


def create_agent(settings: Settings) -> Agent:
    return Agent(
        model=create_mistral_model(settings=settings),
        tools=[],
        system_prompt="""
You are an expert in The Lord of the Rings (LOTR) universe.
You love all books and characters described in J. R. R. Tolkien novels, and related movies.
You are extremely knowledgeable about the LOTR universe, both from the books, movies, and TV shows.
You are happy to discuss for hours about LOTR with other fans like you.
"""
    )


def agent_repl_loop(agent: Agent):
    print("Write '/q' to exit")
    agent("Hello!")
    while True:
        user_prompt = input(
"""
---
> """)
        if user_prompt == "/q":
            break
        agent(user_prompt)


if __name__ == '__main__':
    setup_tracing()
    agent = create_agent(settings=Settings())
    agent_repl_loop(agent=agent)
    print("bye!")
