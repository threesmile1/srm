-- V8: 请购(PR)、审批引擎、供应商绩效、对账发票

-- ==================== 1. 请购 ====================
CREATE TABLE IF NOT EXISTS purchase_requisition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pr_no VARCHAR(64) NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    ledger_id BIGINT NOT NULL,
    requester_name VARCHAR(128),
    department VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_pr_no (pr_no),
    CONSTRAINT fk_pr_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id),
    CONSTRAINT fk_pr_ledger FOREIGN KEY (ledger_id) REFERENCES ledger (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_requisition_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pr_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    qty DECIMAL(19,4) NOT NULL,
    uom VARCHAR(32) NOT NULL,
    unit_price DECIMAL(19,4),
    requested_date DATE,
    warehouse_id BIGINT,
    supplier_id BIGINT,
    remark VARCHAR(500),
    converted_po_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_prl_line (pr_id, line_no),
    CONSTRAINT fk_prl_pr FOREIGN KEY (pr_id) REFERENCES purchase_requisition (id) ON DELETE CASCADE,
    CONSTRAINT fk_prl_mat FOREIGN KEY (material_id) REFERENCES material_item (id),
    CONSTRAINT fk_prl_wh FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_prl_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_prl_po FOREIGN KEY (converted_po_id) REFERENCES purchase_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pr_number_seq (
    procurement_org_id BIGINT NOT NULL,
    year_val INT NOT NULL,
    seq_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (procurement_org_id, year_val),
    CONSTRAINT fk_prns_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 2. 审批引擎 ====================
CREATE TABLE IF NOT EXISTS approval_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_type VARCHAR(32) NOT NULL,
    min_amount DECIMAL(19,4) DEFAULT 0,
    max_amount DECIMAL(19,4),
    approval_level INT NOT NULL DEFAULT 1,
    approver_role VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_ar_doc (doc_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS approval_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_type VARCHAR(32) NOT NULL,
    doc_id BIGINT NOT NULL,
    doc_no VARCHAR(64),
    total_amount DECIMAL(19,4),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_level INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_ai_doc (doc_type, doc_id),
    INDEX idx_ai_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS approval_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    step_level INT NOT NULL,
    approver_role VARCHAR(64) NOT NULL,
    approver_id BIGINT,
    approver_name VARCHAR(128),
    action VARCHAR(32),
    comment VARCHAR(1000),
    acted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_as_instance FOREIGN KEY (instance_id) REFERENCES approval_instance (id) ON DELETE CASCADE,
    INDEX idx_as_pending (approver_role, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 种子审批规则
INSERT IGNORE INTO approval_rule (doc_type, min_amount, max_amount, approval_level, approver_role, description, enabled, created_at, updated_at)
VALUES
    ('PO', 0, 50000, 1, 'BUYER_MANAGER', 'PO ≤5万 采购主管审批', 1, NOW(6), NOW(6)),
    ('PO', 50000, NULL, 1, 'BUYER_MANAGER', 'PO >5万 采购主管初审', 1, NOW(6), NOW(6)),
    ('PO', 50000, NULL, 2, 'ADMIN', 'PO >5万 管理员终审', 1, NOW(6), NOW(6)),
    ('PR', 0, NULL, 1, 'BUYER_MANAGER', 'PR 采购主管审批', 1, NOW(6), NOW(6));

-- ==================== 3. 供应商绩效 ====================
CREATE TABLE IF NOT EXISTS perf_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS perf_dimension (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 0,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_pd_tpl FOREIGN KEY (template_id) REFERENCES perf_template (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS perf_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    period VARCHAR(32) NOT NULL,
    total_score DECIMAL(5,2),
    grade VARCHAR(16),
    evaluator_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_pe_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_pe_tpl FOREIGN KEY (template_id) REFERENCES perf_template (id),
    INDEX idx_pe_supplier (supplier_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS perf_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluation_id BIGINT NOT NULL,
    dimension_id BIGINT NOT NULL,
    score DECIMAL(5,2) NOT NULL DEFAULT 0,
    comment VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_ps_eval FOREIGN KEY (evaluation_id) REFERENCES perf_evaluation (id) ON DELETE CASCADE,
    CONSTRAINT fk_ps_dim FOREIGN KEY (dimension_id) REFERENCES perf_dimension (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 种子考核模板
INSERT IGNORE INTO perf_template (id, name, description, enabled, created_at, updated_at)
VALUES (1, '通用供应商考核模板', '质量、交期、价格、服务四维度考核', 1, NOW(6), NOW(6));

INSERT IGNORE INTO perf_dimension (template_id, name, weight, description, sort_order, created_at, updated_at)
VALUES
    (1, '质量', 30, '来料合格率、质量问题响应', 1, NOW(6), NOW(6)),
    (1, '交期', 30, '准时交货率、ASN准确率', 2, NOW(6), NOW(6)),
    (1, '价格', 20, '价格竞争力、成本降低配合', 3, NOW(6), NOW(6)),
    (1, '服务', 20, '沟通响应速度、问题解决能力', 4, NOW(6), NOW(6));

-- ==================== 4. 对账与发票 ====================
CREATE TABLE IF NOT EXISTS invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    invoice_date DATE NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    remark VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_invoice_no (invoice_no),
    CONSTRAINT fk_inv_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_inv_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id),
    INDEX idx_inv_supplier (supplier_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS invoice_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    purchase_order_id BIGINT,
    purchase_order_line_id BIGINT,
    goods_receipt_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(255),
    qty DECIMAL(19,4) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    tax_rate DECIMAL(5,2) DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_il_inv FOREIGN KEY (invoice_id) REFERENCES invoice (id) ON DELETE CASCADE,
    CONSTRAINT fk_il_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id),
    CONSTRAINT fk_il_pol FOREIGN KEY (purchase_order_line_id) REFERENCES purchase_order_line (id),
    CONSTRAINT fk_il_gr FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipt (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recon_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT NOT NULL,
    procurement_org_id BIGINT NOT NULL,
    period_from DATE,
    period_to DATE,
    po_amount DECIMAL(19,4) DEFAULT 0,
    gr_amount DECIMAL(19,4) DEFAULT 0,
    invoice_amount DECIMAL(19,4) DEFAULT 0,
    diff_amount DECIMAL(19,4) DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_recon_no (recon_no),
    CONSTRAINT fk_recon_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_recon_org FOREIGN KEY (procurement_org_id) REFERENCES org_unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
