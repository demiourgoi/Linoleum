package io.github.demiourgoi.linoleum.examples;

import java.util.List;

/**
 * 
 * https://docs.mistral.ai/api/endpoint/chat#operation-chat_completion_v1_chat_completions_post
 */
public class MistralChatResponse {
    private String id;
    private long created;
    private String model;
    private Usage usage;
    private String object;
    private List<Choice> choices = new java.util.ArrayList<>();

    public static class Usage {
        private int prompt_tokens;
        private int total_tokens;
        private int completion_tokens;
        private int num_cached_tokens;

        public int getPromptTokens() {
            return prompt_tokens;
        }

        public void setPromptTokens(int prompt_tokens) {
            this.prompt_tokens = prompt_tokens;
        }

        public int getTotalTokens() {
            return total_tokens;
        }

        public void setTotalTokens(int total_tokens) {
            this.total_tokens = total_tokens;
        }

        public int getCompletionTokens() {
            return completion_tokens;
        }

        public void setCompletionTokens(int completion_tokens) {
            this.completion_tokens = completion_tokens;
        }

        public int getNumCachedTokens() {
            return num_cached_tokens;
        }

        public void setNumCachedTokens(int num_cached_tokens) {
            this.num_cached_tokens = num_cached_tokens;
        }
    }

    public static class Choice {
        private int index;
        private String finish_reason;
        private Message message;

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getFinishReason() {
            return finish_reason;
        }

        public void setFinishReason(String finish_reason) {
            this.finish_reason = finish_reason;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }

    public static class Message {
        private String role;
        private Object tool_calls;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Object getToolCalls() {
            return tool_calls;
        }

        public void setToolCalls(Object tool_calls) {
            this.tool_calls = tool_calls;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    /**
     * Extracts the content from all choices as a list of strings
     * @return List of content strings from each choice's message
     */
    public List<String> getChoiceContents() {
        List<String> contents = new java.util.ArrayList<>();
        if (choices != null) {
            for (Choice choice : choices) {
                if (choice != null && choice.getMessage() != null) {
                    String content = choice.getMessage().getContent();
                    if (content != null) {
                        contents.add(content);
                    }
                }
            }
        }
        return contents;
    }

    public String getChoiceContentsString() {
        return String.join(", ", getChoiceContents());
    }
}