package io.github.demiourgoi.linoleum.examples;

import java.util.List;

/**
 * 
 * https://docs.mistral.ai/api/endpoint/chat#operation-chat_completion_v1_chat_completions_post
 */
public class MistralChatRequest {
    private List<Message> messages;
    private String model;

    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public MistralChatRequest(List<Message> messages, String model) {
        this.messages = messages;
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}