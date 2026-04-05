CREATE TABLE statement_uploads (
    id                 BIGSERIAL PRIMARY KEY,
    file_hash          VARCHAR(32)  NOT NULL,
    original_filename  VARCHAR(255),
    bank_name          VARCHAR(100),
    statement_type     VARCHAR(50),
    transaction_count  INTEGER      NOT NULL DEFAULT 0,
    uploaded_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uploads_hash ON statement_uploads(file_hash);

CREATE TABLE transactions (
    id            BIGSERIAL PRIMARY KEY,
    upload_id     BIGINT       NOT NULL REFERENCES statement_uploads(id) ON DELETE CASCADE,
    txn_date      DATE         NOT NULL,
    description   TEXT,
    debit         DOUBLE PRECISION NOT NULL DEFAULT 0,
    credit        DOUBLE PRECISION NOT NULL DEFAULT 0,
    balance       DOUBLE PRECISION NOT NULL DEFAULT 0,
    payment_mode  VARCHAR(50),
    merchant_name VARCHAR(255),
    category      VARCHAR(50)
);

CREATE INDEX idx_txn_upload   ON transactions(upload_id);
CREATE INDEX idx_txn_date     ON transactions(txn_date);
CREATE INDEX idx_txn_category ON transactions(category);
