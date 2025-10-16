import os
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from typing import Optional

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


class LotrAgent:
    _settings: Settings
    _agent: Optional[Agent] = None
    _EXIT_COMMAND: str = "/q"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def _setup_tracing(self, enable_console_exporter: bool = False) -> None:
        # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#enabling-tracing
        # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#example-end-to-end-tracing
        strands_telemetry = StrandsTelemetry()
        strands_telemetry.setup_otlp_exporter()     # Send traces to OTLP endpoint
        if enable_console_exporter:
            strands_telemetry.setup_console_exporter()  # Print traces to console

    def _create_mistral_model(self) -> Model:
        # https://docs.mistral.ai/#free-models
        return MistralModel(
            api_key=self._settings.mistral_api_key,
            model_id="mistral-small-latest",
        )

    def _create_agent(self) -> Agent:
        return Agent(
            model=self._create_mistral_model(),
            tools=[],
            system_prompt="""
You are an expert in The Lord of the Rings (LOTR) universe.
You love all books and characters described in J. R. R. Tolkien novels, and related movies.
You are extremely knowledgeable about the LOTR universe, both from the books, movies, and TV shows.
You are happy to discuss for hours about LOTR with other fans like you.
"""
        )

    def _agent_repl_loop(self) -> None:
        print(f"Write '{self._EXIT_COMMAND}' to exit")
        if self._agent is None:
            raise RuntimeError("Agent not initialized. Call init() first.")
        self._agent("Hello!")
        while True:
            user_prompt = input(
"""
---
> """)
            if user_prompt == self._EXIT_COMMAND:
                break
            self._agent(user_prompt)

    def init(self) -> None:
        self._setup_tracing()
        self._agent = self._create_agent()

    def run(self) -> None:
        self._agent_repl_loop()

    def init_and_run(self) -> None:
        if self._agent is None:
            self.init()
        self.run()


if __name__ == '__main__':
    agent = LotrAgent(settings=Settings())
    agent.init_and_run()
    print("bye!")
