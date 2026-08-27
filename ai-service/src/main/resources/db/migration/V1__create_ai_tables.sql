-- Enable pgvector extension
-- This must run before any vector column can be created
CREATE EXTENSION IF NOT EXISTS vector;

-- Chat history — stores every message in every conversation
CREATE TABLE chat_messages (
                               id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id         UUID        NOT NULL,
                               session_id      VARCHAR(255) NOT NULL,
                               role            VARCHAR(20) NOT NULL,   -- 'user' or 'assistant'
                               content         TEXT        NOT NULL,
                               created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_user_session
    ON chat_messages(user_id, session_id, created_at ASC);

-- Transaction embeddings for RAG
-- Each row is one transaction chunk with its vector representation
CREATE TABLE transaction_embeddings (
                                        id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                        user_id         UUID        NOT NULL,
                                        transaction_id  UUID        NOT NULL UNIQUE,
                                        content         TEXT        NOT NULL,   -- human-readable text of the transaction
                                        embedding       vector(768),            -- embedding vector — 768 dims for nomic-embed-text
                                        metadata        JSONB,                  -- category, amount, date — for filtered retrieval
                                        created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- HNSW index for fast approximate nearest-neighbor search
-- Better than IVFFlat for real-time inserts (you are inserting frequently)
CREATE INDEX idx_transaction_embeddings_vector
    ON transaction_embeddings
    USING hnsw (embedding vector_cosine_ops);

-- Filter by user before vector search — critical for multi-user isolation
CREATE INDEX idx_transaction_embeddings_user
    ON transaction_embeddings(user_id);

-- Idempotency table for Kafka consumer
CREATE TABLE processed_events (
                                  event_id        VARCHAR(255) PRIMARY KEY,
                                  processed_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);