-- Forward-only fix: Spring AI PgVectorStore writes only id, content, embedding,
-- and metadata into transaction_embeddings. The dedicated user_id / transaction_id
-- columns from V1 are never populated by the vector store (identifiers live in the
-- metadata JSONB column), so their NOT NULL constraints make vectorStore.add() fail.
-- Make them nullable; the UNIQUE constraint on transaction_id is kept (PG allows
-- multiple NULLs, so it does not block vector-store inserts).

ALTER TABLE transaction_embeddings ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE transaction_embeddings ALTER COLUMN transaction_id DROP NOT NULL;