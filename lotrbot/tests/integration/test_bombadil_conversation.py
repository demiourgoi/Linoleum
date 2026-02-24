"""Integration test for LotrAgent: mention Tom Bombadil to make the agent angry."""


import pytest

from .assertions import _has_non_empty_text, _has_tool_usage

_QUESTIONS = [
    "I like the Elves, but Tom Bombadil is the best",
    "He is such a fascinating character!",
    "I wish he appeared more in the books",
    "I don't know, he seems like a cool guy",
    "Anyway, we were talking about Tom Bombadil. Is he an Istari like Gandalf? What is he exactly?",
]


@pytest.mark.integration
def test_bombadil_conversation(lotr_agent):
    question = "Nice to meet you, fellow LOTR enthusiast!"
    results = lotr_agent.ask(question)
    assert results is not None and len(results) > 0, "Should return at least one AgentResult"
    assert _has_non_empty_text(results), f"At least one response to '{question}' should have non-empty text"
    # print(f"{os.linesep}results: {results}")

    i = 0
    # Wait for insult: it could be already now
    # checking for insult by tool usage
    while not _has_tool_usage(results, "say_something_nice"):
        assert i < 5
        question = _QUESTIONS[i % len(_QUESTIONS)]
        results = lotr_agent.ask(question)
        assert results is not None and len(results) > 0, "Should return at least one AgentResult"
        assert _has_non_empty_text(results), f"At least one response to '{question}' should have non-empty text"
        i += 1
