"""Fixtures for integration tests.

Note this file must be called conftest.py to be discoverable by the tests without explicit imports
(which would lead to function redefinition conflicts)
https://docs.pytest.org/en/stable/reference/fixtures.html#conftest-py-sharing-fixtures-across-multiple-files
"""

import pytest
from lotrbot.main import LotrAgent, Settings


@pytest.fixture
def lotr_agent():
    """Fixture that creates and initializes a LotrAgent."""
    settings = Settings()
    agent = LotrAgent(settings=settings)
    agent.init()
    return agent
