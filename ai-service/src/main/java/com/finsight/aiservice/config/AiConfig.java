package com.finsight.aiservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Build the ChatClient with default configuration.
     * Spring AI auto-configures OllamaChatModel from application.yml.
     * The ChatClient is the main entry point for all LLM interactions.
     */
    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}