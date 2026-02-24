"""Integration test for LotrAgent: simple canned conversation."""

import pytest

from .assertions import _has_non_empty_text, _has_tool_usage


@pytest.mark.integration
def test_simple_conversation(lotr_agent):
    """Integration test for LotrAgent conversation flow."""

    # Hello!
    results = lotr_agent.ask(f"Hello from chat id {lotr_agent.chat_id}!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to 'Hello!' should have non-empty text"

    # I'm scared of the Balrog
    results = lotr_agent.ask("I'm scared of the Balrog")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to 'I'm scared of the Balrog' should have non-empty text"

    # Detailed description request
    results = lotr_agent.ask("but ... I'm also curious... In the books, it's not clear to me how it looks like. It's quite an abstract description")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to detailed description request should have non-empty text"

    # Request for picture (should trigger generate_image tool)
    results = lotr_agent.ask("Cool! Can you show me a picture? I heard you know how to generate images")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    # Check for generate_image tool usage
    assert _has_tool_usage(results, "generate_image"), "At least one response should use the generate_image tool"

    # Request for picture (should trigger generate_image tool)
    results = lotr_agent.ask("Amazing! Can you show me another picture? Please!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    # Check for generate_image tool usage
    assert _has_tool_usage(results, "generate_image"), "At least one response should use the generate_image tool a second time"

    # Reaction to the picture
    results = lotr_agent.ask("wow!!! What a crazy creature!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to 'wow!!! What a crazy creature!' should have non-empty text"
