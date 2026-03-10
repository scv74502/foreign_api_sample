CREATE TABLE task (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    status     VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
