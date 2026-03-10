ALTER TABLE task ADD COLUMN idempotency_key VARCHAR(255) NOT NULL DEFAULT '' COMMENT '멱등성 키 (중복 요청 방지)';

ALTER TABLE task ADD COLUMN image_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '처리 대상 이미지 URL';

ALTER TABLE task ADD COLUMN external_job_id VARCHAR(255) NULL COMMENT '외부 서비스(Mock Worker)에서 발급받은 작업 ID';

ALTER TABLE task ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수';

ALTER TABLE task ADD COLUMN result TEXT NULL COMMENT '처리 결과 (완료 시)';

ALTER TABLE task ADD COLUMN error_code VARCHAR(100) NULL COMMENT '에러 코드 (실패 시)';

ALTER TABLE task ADD COLUMN error_message TEXT NULL COMMENT '에러 메시지 (실패 시)';

CREATE UNIQUE INDEX idx_idempotency_key ON task (idempotency_key);
