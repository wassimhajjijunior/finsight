package com.finsight.aiservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Build the ChatClient with default configuration.
     * Spring AI auto-configures the ChatModel bean from application.yml
     * (OpenAiChatModel via Groq in this case).
     * The ChatClient is the main entry point for all LLM interactions.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}