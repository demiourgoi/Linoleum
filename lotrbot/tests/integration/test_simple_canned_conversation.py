"""Integration test for LotrAgent."""

import uuid
import pytest
from lotrbot.main import LotrAgent, Settings


@pytest.fixture
def lotr_agent():
    """Fixture that creates and initializes a LotrAgent."""
    settings = Settings()
    agent = LotrAgent(settings=settings)
    agent.init()
    return agent


@pytest.fixture
def test_run_id():
    """Fixture that generates a unique test run ID for the test session."""
    return str(uuid.uuid4())


def _has_non_empty_text(agent_results):
    """Check if any agent result has non-empty text content."""
    for result in agent_results:
        # AgentResult has a message attribute that contains the response
        if hasattr(result, 'message') and result.message:
            # The message contains content array with text blocks
            content_array = result.message.get('content', [])
            for item in content_array:
                if isinstance(item, dict) and 'text' in item:
                    text_content = item.get('text', '')
                    if text_content and len(text_content.strip()) > 0:
                        return True
    return False


def _has_tool_usage(agent_results, tool_name):
    """Check if any agent result includes usage of the specified tool."""
    for result in agent_results:
        # Check the metrics for tool usage
        if hasattr(result, 'metrics') and result.metrics:
            # Check if the tool name exists in the tool_metrics dictionary
            if tool_name in result.metrics.tool_metrics:
                return True
    return False


@pytest.mark.integration
def test_simple_conversation(lotr_agent, test_run_id):
    """Integration test for LotrAgent conversation flow."""

    # Hello!
    results = lotr_agent.ask(f"Hello from test id {test_run_id}!")
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
    results = lotr_agent.ask("Cool! Can you show me a picture?")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    ## Check for generate_image tool usage
    assert _has_tool_usage(results, "generate_image"), "At least one response should use the generate_image tool"

    # Request for picture (should trigger generate_image tool)
    results = lotr_agent.ask("Amazing! Can you show me another picture? Please!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    ## Check for generate_image tool usage
    assert _has_tool_usage(results, "generate_image"), "At least one response should use the generate_image tool"

    # Reaction to the picture
    results = lotr_agent.ask("wow!!! What a crazy creature!")
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), "At least one response to 'wow!!! What a crazy creature!' should have non-empty text"
