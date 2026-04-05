-- A5 ASN、A6 收货、A7 导出状态
-- 幂等：V4 曾部分执行后重跑时跳过已有列/表。

SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'export_status') > 0,
        'SELECT 1',
        'ALTER TABLE purchase_order ADD COLUMN export_status VARCHAR(32) NOT NULL DEFAULT ''NOT_EXPORTED'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'purchase_order_line' AND COLUMN_NAME = 'received_qty') > 0,
        'SELECT 1',
        'ALTER TABLE purchase_order_line ADD COLUMN received_qty DECIMAL(19, 4) NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS asn_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asn_no VARCHAR(64) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    ship_date DATE NOT NULL,
    eta_date DATE,
    carrier VARCHAR(255),
    tracking_no VARCHAR(128),
    remark VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_asn_no (asn_no),
    CONSTRAINT fk_asn_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id),
    CONSTRAINT fk_asn_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_asn_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asn_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asn_notice_id BIGINT NOT NULL,
    purchase_order_line_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    ship_qty DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_asn_line (asn_notice_id, line_no),
    CONSTRAINT fk_asnl_asn FOREIGN KEY (asn_notice_id) REFERENCES asn_notice (id) ON DELETE CASCADE,
    CONSTRAINT fk_asnl_pol FOREIGN KEY (purchase_order_line_id) REFERENCES purchase_order_line (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS goods_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gr_no VARCHAR(64) NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    ledger_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    remark VARCHAR(1000),
    export_status VARCHAR(32) NOT NULL DEFAULT 'NOT_EXPORTED',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_gr_no (gr_no),
    CONSTRAINT fk_gr_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id),
    CONSTRAINT fk_gr_ledger FOREIGN KEY (ledger_id) REFERENCES ledger (id),
    CONSTRAINT fk_gr_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_gr_wh FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_gr_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS goods_receipt_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    purchase_order_line_id BIGINT NOT NULL,
    asn_line_id BIGINT,
    line_no INT NOT NULL,
    received_qty DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_grl_line (goods_receipt_id, line_no),
    CONSTRAINT fk_grl_gr FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipt (id) ON DELETE CASCADE,
    CONSTRAINT fk_grl_pol FOREIGN KEY (purchase_order_line_id) REFERENCES purchase_order_line (id),
    CONSTRAINT fk_grl_asnl FOREIGN KEY (asn_line_id) REFERENCES asn_line (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gr_number_seq (
    procurement_org_id BIGINT NOT NULL,
    year_val INT NOT NULL,
    seq_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (procurement_org_id, year_val),
    CONSTRAINT fk_grns_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asn_number_seq (
    procurement_org_id BIGINT NOT NULL,
    year_val INT NOT NULL,
    seq_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (procurement_org_id, year_val),
    CONSTRAINT fk_asnns_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
