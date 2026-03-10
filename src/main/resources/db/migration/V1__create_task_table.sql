CREATE TABLE task (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '작업 ID (기본 키)',
    status     VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT '작업 상태',
    created_at DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6)  NOT NULL COMMENT '수정 시각',
    version    BIGINT       NOT NULL DEFAULT 0 COMMENT '낙관적 잠금 버전',
    PRIMARY KEY (id),
    INDEX idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
