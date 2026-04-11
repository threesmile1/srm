-- 物料 × 供应商（U9 帆软 lpgys.cpt 按料号拉取，一行一供应商）

CREATE TABLE material_supplier_u9 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    supplier_code VARCHAR(64) NOT NULL COMMENT '供应商编码（报表 code）',
    supplier_name VARCHAR(255) NULL COMMENT '供应商名称（报表 gongyingshang）',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_msu9_mat_sup (material_id, supplier_code),
    KEY idx_msu9_material (material_id),
    CONSTRAINT fk_msu9_material FOREIGN KEY (material_id) REFERENCES material_item (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
