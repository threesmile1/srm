-- 供应商门户上传的发票附件（PDF/图片等）

CREATE TABLE IF NOT EXISTS invoice_attachment (
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
