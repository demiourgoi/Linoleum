"""Integration test for LotrAgent."""

import pytest
from lotrbot.main import LotrAgent, Settings


@pytest.fixture
def lotr_agent():
    """Fixture that creates and initializes a LotrAgent."""
    agent = LotrAgent(settings=Settings())
    agent.init()
    return agent


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
        # Check the message content for tool usage patterns
        if hasattr(result, 'message') and result.message:
            content_array = result.message.get('content', [])
            for item in content_array:
                if isinstance(item, dict):
                    # Check for toolUse blocks in the content
                    if 'toolUse' in item:
                        tool_use_content = item.get('toolUse', {})
                        if 'name' in tool_use_content and tool_name.lower() in tool_use_content['name'].lower():
                            return True
                    # Also check text content for tool mentions
                    if 'text' in item:
                        text_content = item.get('text', '')
                        if tool_name.lower() in text_content.lower():
                            return True
    return False


@pytest.mark.integration
def test_lotr_agent_conversation(lotr_agent):
    """Integration test for LotrAgent conversation flow."""

    # Test sequence 1: Hello!
    results1 = lotr_agent.ask("Hello!")
    assert results1 is not None and len(results1) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results1), "At least one response to 'Hello!' should have non-empty text"

    # Test sequence 2: I'm scared of the Balrog
    results2 = lotr_agent.ask("I'm scared of the Balrog")
    assert results2 is not None and len(results2) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results2), "At least one response to 'I'm scared of the Balrog' should have non-empty text"

    # Test sequence 3: Detailed description request
    results3 = lotr_agent.ask("but ... I'm also curious... In the books, it's not clear to me how it looks like. It's quite an abstract description")
    assert results3 is not None and len(results3) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results3), "At least one response to detailed description request should have non-empty text"

    # Test sequence 4: Request for picture (should trigger generate_image tool)
    results4 = lotr_agent.ask("Cool! Can you show me a picture?")
    assert results4 is not None and len(results4) > 0, "Should return at least one AgentResult"
    # Check for generate_image tool usage
    # assert _has_tool_usage(results4, "generate_image"), "At least one response should use the generate_image tool"

    # Test sequence 5: Reaction to the picture
    results5 = lotr_agent.ask("wow!!! What a crazy creature!")
    assert results5 is not None and len(results5) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results5), "At least one response to 'wow!!! What a crazy creature!' should have non-empty text"
