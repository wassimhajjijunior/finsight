CREATE TYPE budget_period AS ENUM (
    'WEEKLY', 'MONTHLY', 'YEARLY'
);

CREATE TYPE budget_category AS ENUM (
    'FOOD', 'TRANSPORT', 'HOUSING', 'HEALTHCARE',
    'ENTERTAINMENT', 'SHOPPING', 'SALARY', 'INVESTMENT',
    'TRANSFER', 'OTHER'
);

CREATE TABLE budgets (
                         id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                         user_id             UUID            NOT NULL,
                         account_id          UUID            NOT NULL,
                         name                VARCHAR(100)    NOT NULL,
                         category            budget_category NOT NULL,
                         amount_limit        DECIMAL(19, 4)  NOT NULL,
                         spent_amount        DECIMAL(19, 4)  NOT NULL DEFAULT 0.0000,
                         currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
                         period              budget_period   NOT NULL,
                         period_start        DATE            NOT NULL,
                         period_end          DATE            NOT NULL,
                         alert_threshold     INTEGER         NOT NULL DEFAULT 80,
                         alert_sent          BOOLEAN         NOT NULL DEFAULT FALSE,
                         created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
                         updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budgets_user_id
    ON budgets(user_id);

CREATE INDEX idx_budgets_user_category
    ON budgets(user_id, category);

-- Enforce one budget per user per category per period
CREATE UNIQUE INDEX idx_budgets_unique_period
    ON budgets(user_id, account_id, category, period_start, period_end);