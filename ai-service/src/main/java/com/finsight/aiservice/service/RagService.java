package com.finsight.aiservice.service;

import com.finsight.aiservice.dto.ChatRequest;
import com.finsight.aiservice.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final ToolService toolService;
    private final JdbcTemplate jdbcTemplate;

    private static final String SYSTEM_PROMPT = """
            You are a personal finance assistant for FinSight.
            You help users understand their spending, track budgets,
            and make sense of their financial data.
            
            Rules:
            - Only discuss the user's own financial data
            - Be specific with numbers when you have them
            - If you are not sure, say so — do not invent numbers
            - Use the provided tools to fetch live data when needed
            - Use the provided transaction history context for historical questions
            - Keep answers concise and actionable
            """;

    /**
     * Process a chat message using RAG + tool calling.
     *
     * Flow:
     * 1. Save user message to chat history
     * 2. Retrieve relevant past transactions from pgvector (RAG)
     * 3. Load recent chat history for conversation continuity
     * 4. Call LLM with: system prompt + history + RAG context + tools
     * 5. LLM may call tools (getAccountBalances, getRecentTransactions)
     * 6. Save assistant response to chat history
     * 7. Return response
     */
    @Transactional
    public ChatResponse chat(String userId, ChatRequest request) {

        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        // Step 1: Save user message
        saveMessage(userId, sessionId, "user", request.getMessage());

        // Step 2: Retrieve relevant transactions from pgvector
        List<Document> relevantDocs = embeddingService
                .retrieveRelevantTransactions(request.getMessage(), userId, 5);

        String ragContext = buildRagContext(relevantDocs);

        // Step 3: Load recent conversation history (last 10 messages)
        String conversationHistory = loadConversationHistory(
                userId, sessionId, 10);

        // Step 4 + 5: Set userId for tool calls, then call LLM
        ToolService.setCurrentUser(userId);
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(
                            request.getMessage(),
                            ragContext,
                            conversationHistory))
                    .tools(toolService)   // register tool methods
                    .call()
                    .content();
        } finally {
            // Always clear — even if an exception occurs
            ToolService.clearCurrentUser();
        }

        // Step 6: Save assistant response
        saveMessage(userId, sessionId, "assistant", answer);

        log.info("Chat processed: userId={} sessionId={} " +
                "ragDocsUsed={}", userId, sessionId, relevantDocs.size());

        return ChatResponse.builder()
                .answer(answer)
                .sessionId(sessionId)
                .sourcesUsed(relevantDocs.stream()
                        .map(d -> (String) d.getMetadata()
                                .getOrDefault("transactionId", ""))
                        .collect(Collectors.toList()))
                .usedLiveData(true)
                .build();
    }

    private String buildUserPrompt(String question,
                                   String ragContext,
                                   String history) {
        StringBuilder prompt = new StringBuilder();

        if (!history.isBlank()) {
            prompt.append("Conversation history:\n")
                    .append(history)
                    .append("\n\n");
        }

        if (!ragContext.isBlank()) {
            prompt.append("Relevant past transactions from your history:\n")
                    .append(ragContext)
                    .append("\n\n");
        }

        prompt.append("User question: ").append(question);
        return prompt.toString();
    }

    private String buildRagContext(List<Document> docs) {
        if (docs.isEmpty()) return "";
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n- ", "- ", ""));
    }

    private String loadConversationHistory(String userId,
                                           String sessionId,
                                           int limit) {
        List<String> messages = jdbcTemplate.queryForList(
                "SELECT role || ': ' || content FROM chat_messages " +
                        "WHERE user_id = ? AND session_id = ? " +
                        "ORDER BY created_at DESC LIMIT ?",
                String.class,
                UUID.fromString(userId), sessionId, limit
        );

        // Reverse to get chronological order
        java.util.Collections.reverse(messages);
        return String.join("\n", messages);
    }

    private void saveMessage(String userId, String sessionId,
                             String role, String content) {
        jdbcTemplate.update(
                "INSERT INTO chat_messages " +
                        "(user_id, session_id, role, content) " +
                        "VALUES (?, ?, ?, ?)",
                UUID.fromString(userId), sessionId, role, content
        );
    }
}