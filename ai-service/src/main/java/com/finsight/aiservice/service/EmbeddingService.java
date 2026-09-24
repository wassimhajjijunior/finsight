package com.finsight.aiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Index a transaction event received from Kafka.
     *
     * Converts the transaction into a human-readable text
     * representation, creates a Spring AI Document, and stores
     * it in pgvector. The VectorStore handles embedding generation
     * and storage automatically.
     */
    @Transactional
    public void indexTransaction(Map<String, Object> transactionEvent) {
        String transactionId = (String) transactionEvent.get("transactionId");
        String userId = (String) transactionEvent.get("userId");

        // Check if already indexed (idempotency)
        // transaction_id column is never populated by PgVectorStore —
        // the identifier lives in the metadata JSONB column.
        Boolean alreadyIndexed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM transaction_embeddings " +
                        "WHERE metadata->>'transactionId' = ?",
                Boolean.class,
                transactionId
        );

        if (Boolean.TRUE.equals(alreadyIndexed)) {
            log.debug("Transaction already indexed: {}", transactionId);
            return;
        }

        // Build human-readable text for embedding
        // The quality of this text directly affects RAG answer quality
        String content = buildTransactionText(transactionEvent);

        // Spring AI Document — contains text + metadata
        // Metadata enables filtered retrieval (only this user's transactions)
        Document document = new Document(
                content,
                Map.of(
                        "transactionId", transactionId,
                        "userId", userId,
                        "category", transactionEvent.getOrDefault("category", "OTHER"),
                        "type", transactionEvent.getOrDefault("type", "UNKNOWN"),
                        "amount", String.valueOf(transactionEvent.getOrDefault("amount", 0))
                )
        );

        // VectorStore.add() handles:
        // 1. Calling the embedding model to convert text to vector
        // 2. Storing text + vector + metadata in pgvector
        vectorStore.add(List.of(document));

        log.info("Transaction indexed for RAG: transactionId={} userId={}",
                transactionId, userId);
    }

    /**
     * Build a rich text representation of the transaction.
     *
     * This text is what gets embedded and what appears in the
     * LLM context during RAG. More descriptive = better retrieval.
     * Vague text = poor retrieval = wrong answers.
     */
    private String buildTransactionText(Map<String, Object> event) {
        return String.format(
                "Transaction: %s of %s %s in category %s. " +
                        "Merchant: %s. Description: %s. " +
                        "Date: %s.",
                event.getOrDefault("type", "UNKNOWN"),
                event.getOrDefault("amount", "0"),
                event.getOrDefault("currency", "USD"),
                event.getOrDefault("category", "OTHER"),
                event.getOrDefault("merchant", "unknown merchant"),
                event.getOrDefault("description", "no description"),
                event.getOrDefault("transactionDate", "unknown date")
        );
    }

    /**
     * Retrieve relevant transactions for a user query.
     *
     * Filters by userId first — critical for user isolation.
     * Then performs semantic similarity search.
     * Returns top-k most relevant transaction texts.
     */
    public List<Document> retrieveRelevantTransactions(
            String query, String userId, int topK) {

        // FilterExpression ensures we only search this user's vectors
        // Never return another user's financial data
        return vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression("userId == '" + userId + "'")
                        .build()
        );
    }
}