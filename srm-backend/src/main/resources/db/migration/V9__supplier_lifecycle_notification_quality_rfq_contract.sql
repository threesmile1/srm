-- ========== Supplier Lifecycle ==========
-- 标准 MySQL 不支持 ADD COLUMN IF NOT EXISTS；本迁移由 Flyway 单次执行，失败重跑前勿手工加同名列
ALTER TABLE supplier ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'QUALIFIED';
ALTER TABLE supplier ADD COLUMN contact_name VARCHAR(128);
ALTER TABLE supplier ADD COLUMN contact_phone VARCHAR(64);
ALTER TABLE supplier ADD COLUMN contact_email VARCHAR(128);
ALTER TABLE supplier ADD COLUMN address VARCHAR(500);
ALTER TABLE supplier ADD COLUMN bank_name VARCHAR(128);
ALTER TABLE supplier ADD COLUMN bank_account VARCHAR(64);
ALTER TABLE supplier ADD COLUMN business_scope VARCHAR(1000);
ALTER TABLE supplier ADD COLUMN registration_remark VARCHAR(1000);

CREATE TABLE IF NOT EXISTS supplier_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    audit_type VARCHAR(32) NOT NULL COMMENT 'ADMISSION/ANNUAL/RECTIFICATION',
    audit_date DATE NOT NULL,
    result VARCHAR(32) NOT NULL COMMENT 'PASS/FAIL/CONDITIONAL',
    score INT,
    auditor_name VARCHAR(64),
    remark VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== Notification / Message ==========
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT,
    recipient_supplier_id BIGINT,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000),
    category VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/APPROVAL/DELIVERY/INVOICE',
    ref_type VARCHAR(32),
    ref_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notif_user (recipient_user_id, is_read),
    INDEX idx_notif_supplier (recipient_supplier_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== Quality ==========
CREATE TABLE IF NOT EXISTS quality_inspection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inspection_no VARCHAR(64) NOT NULL UNIQUE,
    goods_receipt_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    inspection_date DATE NOT NULL,
    inspector_name VARCHAR(64),
    result VARCHAR(32) NOT NULL COMMENT 'PASS/FAIL/CONDITIONAL',
    total_qty DECIMAL(19,4) NOT NULL,
    qualified_qty DECIMAL(19,4) NOT NULL DEFAULT 0,
    defect_qty DECIMAL(19,4) NOT NULL DEFAULT 0,
    defect_type VARCHAR(128),
    remark VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipt(id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    FOREIGN KEY (procurement_org_id) REFERENCES org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS corrective_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_no VARCHAR(64) NOT NULL UNIQUE,
    inspection_id BIGINT,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    issue_description VARCHAR(2000) NOT NULL,
    root_cause VARCHAR(2000),
    corrective_measures VARCHAR(2000),
    due_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/CLOSED/OVERDUE',
    closed_date DATE,
    remark VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES quality_inspection(id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    FOREIGN KEY (procurement_org_id) REFERENCES org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== RFQ (Request for Quotation) ==========
CREATE TABLE IF NOT EXISTS rfq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_no VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/EVALUATING/AWARDED/CANCELLED',
    publish_date DATE,
    deadline DATE,
    remark VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (procurement_org_id) REFERENCES org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rfq_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(19,4) NOT NULL,
    uom VARCHAR(32) NOT NULL,
    specification VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (material_id) REFERENCES material_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rfq_invitation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    invited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rfq_supplier (rfq_id, supplier_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rfq_quotation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    total_amount DECIMAL(19,4),
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    delivery_days INT,
    validity_days INT,
    remark VARCHAR(2000),
    submitted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rfq_quot_supplier (rfq_id, supplier_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rfq_quotation_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    rfq_line_id BIGINT NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    amount DECIMAL(19,4),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (quotation_id) REFERENCES rfq_quotation(id),
    FOREIGN KEY (rfq_line_id) REFERENCES rfq_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== Contract ==========
CREATE TABLE IF NOT EXISTS contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_no VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/EXPIRED/TERMINATED',
    contract_type VARCHAR(32) DEFAULT 'FRAMEWORK' COMMENT 'FRAMEWORK/SPOT',
    start_date DATE,
    end_date DATE,
    total_amount DECIMAL(19,4),
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    remark VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    FOREIGN KEY (procurement_org_id) REFERENCES org_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS contract_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT,
    material_desc VARCHAR(255),
    qty DECIMAL(19,4),
    uom VARCHAR(32),
    unit_price DECIMAL(19,4),
    amount DECIMAL(19,4),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES contract(id),
    FOREIGN KEY (material_id) REFERENCES material_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
