"""Integration test for LotrAgent: mention Tom Bombadil to make the agent angry."""

import pytest

from .assertions import _has_non_empty_text


@pytest.mark.integration
def test_bombadil_conversation(lotr_agent):
    # Hello!
    results = lotr_agent.ask(f"Hello from chat id {lotr_agent.chat_id}!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to 'Hello!' should have non-empty text"
