ALTER TABLE task
    ADD COLUMN image_url_hash CHAR(64) NOT NULL DEFAULT '' AFTER image_url;

-- 기존 레코드의 image_url_hash를 SHA-256으로 채움
UPDATE task SET image_url_hash = SHA2(image_url, 256) WHERE image_url_hash = '';

CREATE INDEX idx_image_url_hash_created_at ON task (image_url_hash, created_at);
