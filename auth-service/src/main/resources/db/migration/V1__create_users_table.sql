CREATE TABLE users (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email       VARCHAR(255) NOT NULL UNIQUE,
                       password    VARCHAR(255) NOT NULL,
                       first_name  VARCHAR(100) NOT NULL,
                       last_name   VARCHAR(100) NOT NULL,
                       role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
                       enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);