CREATE TYPE transaction_type AS ENUM (
    'INCOME', 'EXPENSE', 'TRANSFER'
);

CREATE TYPE transaction_category AS ENUM (
    'FOOD', 'TRANSPORT', 'HOUSING', 'HEALTHCARE',
    'ENTERTAINMENT', 'SHOPPING', 'SALARY', 'INVESTMENT',
    'TRANSFER', 'OTHER'
);

CREATE TABLE transactions (
                              id              UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),
                              user_id         UUID                    NOT NULL,
                              account_id      UUID                    NOT NULL,
                              type            transaction_type        NOT NULL,
                              category        transaction_category    NOT NULL DEFAULT 'OTHER',
                              amount          DECIMAL(19, 4)          NOT NULL,
                              currency        VARCHAR(3)              NOT NULL DEFAULT 'USD',
                              description     VARCHAR(500),
                              merchant        VARCHAR(200),
                              transaction_date TIMESTAMP              NOT NULL DEFAULT NOW(),
                              created_at      TIMESTAMP               NOT NULL DEFAULT NOW()
);

-- Most common query: get transactions for a user
CREATE INDEX idx_transactions_user_id
    ON transactions(user_id);

-- Filter by account
CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

-- Date range queries — very common in finance
CREATE INDEX idx_transactions_user_date
    ON transactions(user_id, transaction_date DESC);

-- Category filtering for budget analysis
CREATE INDEX idx_transactions_user_category
    ON transactions(user_id, category);