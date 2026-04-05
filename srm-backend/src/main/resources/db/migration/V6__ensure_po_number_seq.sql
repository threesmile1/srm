-- 修复历史库：Flyway 已高于 V3 但从未成功创建 po_number_seq（如早期 V3 失败/手工清表等）

CREATE TABLE IF NOT EXISTS po_number_seq (
    procurement_org_id BIGINT NOT NULL,
    year_val INT NOT NULL,
    seq_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (procurement_org_id, year_val),
    CONSTRAINT fk_pons_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
