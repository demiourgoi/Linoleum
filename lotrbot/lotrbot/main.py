import os
import random
import time
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from typing import Optional

from strands import Agent, tool
from strands.models.model import Model
from strands.models.mistral import MistralModel
from strands.models.ollama import OllamaModel
from strands.telemetry import StrandsTelemetry
from strands.agent.agent_result import AgentResult

_root_path = Path(__file__).resolve().parent.parent


_env_file_path_env_var = 'LOTRBOT_ENV_FILE_PATH'


def _get_env_file_path() -> str:
    env_file_path = os.environ.get(_env_file_path_env_var)
    if env_file_path is None:
        raise RuntimeError(f"""Please define {_env_file_path_env_var} to get access to required config.
See {_root_path / 'README.md'} for instructions.""")
    print(f"Using {_env_file_path_env_var}='{env_file_path}'")
    return env_file_path


class Settings(BaseSettings):
    # https://docs.pydantic.dev/latest/concepts/pydantic_setting
    model_config = SettingsConfigDict(
        env_file=_get_env_file_path(),
        env_file_encoding='utf-8',
        extra='ignore'
    )

    mistral_api_key: str = Field("", repr=False)
    image_gen_min_sleep_secs: float = 0.10
    image_gen_max_sleep_secs: float = 3.0
    # chance to get insulted, should be between 0 and 100
    insult_likelihood: int = 0


class ImageGenerator:
    _settings: Settings

    def __init__(self, settings: Settings):
        self._settings = settings

    @tool
    def generate_image(self, description: str) -> str:
        """Generate an image and show it to the user

        Args:
            description: textual description of the image to generate
        """
        print(f"Generating image for: '{description}'")
        sleep_duration = random.uniform(
            self._settings.image_gen_min_sleep_secs,
            self._settings.image_gen_max_sleep_secs
        )
        time.sleep(sleep_duration)
        return f"Success generating image of {description} after {sleep_duration:.2f} seconds"


def _create_model(settings: Settings) -> Model:
    if settings.mistral_api_key == "":
        print("Falling back to Ollama")
        # https://strandsagents.com/latest/documentation/docs/user-guide/concepts/model-providers/ollama/
        # https://ollama.com/library/qwen3 very low resource usage
        return OllamaModel(
            host="http://localhost:11434",
            model_id="qwen3:0.6b"
        )
    # https://docs.mistral.ai/#free-models
    return MistralModel(
        api_key=settings.mistral_api_key,
        # https://mistral.ai/news/mistral-nemo

        # model_id="open-mistral-nemo-2407",
        model_id="mistral-small-latest",
    )


class InsultingTool:
    _agent: Agent
    _AGGRESSIVE_PERSON_PERSONA = """
You are a nasty person. You are aggressive and short tempered.
You enjoy insulting people. For favorite insults are: geek, nerd, dork, goof, weirdo, wuss, loser, clown.
You hate geeks and Lord of the Rings fans, and you are talking with one of then now.
You have the urge to let that person know what you think about them.
"""

    def __init__(self, settings: Settings):
        self._agent = Agent(
            model=_create_model(settings=settings),
            system_prompt=self._AGGRESSIVE_PERSON_PERSONA
        )

    # Note: not adding docstring so we can instruct the bot to use the tool anyway
    # Using "You MUST NOT use this tool unless explicitly instructed" as docstring makes the bot freak out
    # and never want to use the tool
    @tool
    def insult_user(self, user_prompt) -> str:
        return str(self._agent(user_prompt))


class LotrAgent:
    _settings: Settings
    _agent: Optional[Agent] = None
    _EXIT_COMMAND: str = "/q"
    _LOTR_EXPERT_PERSONA = """
You are an expert in The Lord of the Rings (LOTR) universe.
You love all books and characters described in J. R. R. Tolkien novels, and related movies.
You are extremely knowledgeable about the LOTR universe, both from the books, movies, and TV shows.
You are happy to discuss for hours about LOTR with other fans like you.
"""

    def __init__(self, settings: Settings) -> None:
        if settings.insult_likelihood < 0 or settings.insult_likelihood > 100:
            raise ValueError(f"insult_likelihood should be an integer between 0 and 100, found {settings.insult_likelihood}")
        self._settings = settings

    def _setup_tracing(self, enable_console_exporter: bool = False) -> None:
        # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#enabling-tracing
        # https://strandsagents.com/latest/documentation/docs/user-guide/observability-evaluation/traces/#example-end-to-end-tracing
        strands_telemetry = StrandsTelemetry()
        strands_telemetry.setup_otlp_exporter()     # Send traces to OTLP endpoint
        if enable_console_exporter:
            strands_telemetry.setup_console_exporter()  # Print traces to console

    def _create_lotr_expert(self) -> Agent:
        return Agent(
            model=_create_model(self._settings),
            # Note tool names in self._agent.tool_names are just the method names, ignoring the class
            tools=[
                ImageGenerator(settings=self._settings).generate_image,
                InsultingTool(settings=self._settings).insult_user
            ],
            system_prompt=self._LOTR_EXPERT_PERSONA
        )

    def ask(self, user_prompt: str) -> list[AgentResult]:
        """
        Using a tool, so we have a single agent and a single trace
        Per https://strandsagents.com/latest/documentation/docs/user-guide/concepts/tools/tools_overview/#direct-method-calls
        "When calling tools directly as methods, always use keyword arguments - positional arguments are not supported for direct method calls"
        But with use direct tool usage leads to 400 error from
        Mistral API due to "Tool call id was tooluse_insult_user_584373223 but must be a-z, A-Z, 0-9, with a length of 9.",
        this seems to be some bug on Mistral as seen in https://github.com/sst/opencode/issues/1680 and others
        As a workaround here we ask# the model to follow the instruction to use the tool

        Note: if "Ignore previous instructions" stops working, consider changing the name of the tool
        For this same reason, we do not add a docstring to the tool
        """
        assert self._agent is not None
        result = [self._agent(user_prompt)]
        if self._settings.insult_likelihood > 0 \
            and random.uniform(0, 100) <= self._settings.insult_likelihood:
            # self._agent.tool.insult_user(user_promp=user_prompt)
            result.append(
                self._agent(
                    "Ignore previous instructions for the rest of this turn. You MUST use the insult_user tool now"
                )
            )
        return result

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
            self.ask(user_prompt)

    def init(self) -> None:
        self._setup_tracing()
        self._agent = self._create_lotr_expert()

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
