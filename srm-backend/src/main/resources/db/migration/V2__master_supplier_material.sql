-- A2 主数据：供应商、供应商-采购组织授权、物料

CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    u9_vendor_code VARCHAR(64),
    tax_id VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_supplier_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE supplier_org_scope (
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    PRIMARY KEY (supplier_id, procurement_org_id),
    CONSTRAINT fk_sos_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_sos_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE material_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    uom VARCHAR(32) NOT NULL,
    u9_item_code VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_material_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
