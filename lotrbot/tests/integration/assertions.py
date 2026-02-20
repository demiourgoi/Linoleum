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
