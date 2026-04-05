-- A3 采购订单 + 门户用户关联供应商；A4 协同字段

ALTER TABLE sys_user
    ADD COLUMN supplier_id BIGINT NULL,
    ADD CONSTRAINT fk_user_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id);

CREATE TABLE po_number_seq (
    procurement_org_id BIGINT NOT NULL,
    year_val INT NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (procurement_org_id, year_val),
    CONSTRAINT fk_pons_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_no VARCHAR(64) NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    ledger_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(32) NOT NULL,
    revision_no INT NOT NULL DEFAULT 1,
    remark VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_po_no (po_no),
    CONSTRAINT fk_po_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id),
    CONSTRAINT fk_po_ledger FOREIGN KEY (ledger_id) REFERENCES ledger (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_order_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(19, 4) NOT NULL,
    uom VARCHAR(32) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    requested_date DATE,
    warehouse_id BIGINT NOT NULL,
    confirmed_qty DECIMAL(19, 4),
    promised_date DATE,
    supplier_remark VARCHAR(500),
    confirmed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_pol_po_line (purchase_order_id, line_no),
    CONSTRAINT fk_pol_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_pol_material FOREIGN KEY (material_id) REFERENCES material_item (id),
    CONSTRAINT fk_pol_wh FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
