CREATE TABLE customer_trading_value_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    branch_id VARCHAR(20) NOT NULL,
    pcc_id VARCHAR(20) NOT NULL,
    total_trading_value DECIMAL(20, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_customer_trading_value_customer CHECK (customer_id > 0),
    CONSTRAINT chk_customer_trading_value_amount CHECK (total_trading_value >= 0)
) ENGINE = InnoDB;
