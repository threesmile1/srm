-- 修复：若 V33 因「表已存在」未执行完整 DDL，导致列缺失、Hibernate validate 失败
DROP TABLE IF EXISTS invoice_attachment;

CREATE TABLE invoice_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(200),
    file_size BIGINT NOT NULL,
    stored_path VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_ia_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id) ON DELETE CASCADE,
    INDEX idx_ia_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
