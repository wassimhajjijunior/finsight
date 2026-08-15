CREATE TYPE account_type AS ENUM ('CHECKING', 'SAVINGS', 'INVESTMENT');
CREATE TYPE account_status AS ENUM ('ACTIVE', 'INACTIVE', 'FROZEN');

CREATE TABLE accounts (
                          id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id     UUID            NOT NULL,
                          name        VARCHAR(100)    NOT NULL,
                          type        account_type    NOT NULL,
                          status      account_status  NOT NULL DEFAULT 'ACTIVE',
                          balance     DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
                          currency    VARCHAR(3)      NOT NULL DEFAULT 'USD',
                          created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Every query filters by user_id — this index is critical
CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- Composite index for filtering by user + status
CREATE INDEX idx_accounts_user_status ON accounts(user_id, status);