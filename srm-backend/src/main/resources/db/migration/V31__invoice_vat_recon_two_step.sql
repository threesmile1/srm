-- 税务发票字段 + 对账两步确认（供应商 → 采购）

ALTER TABLE invoice
    ADD COLUMN vat_invoice_code VARCHAR(20) NULL COMMENT '税务发票代码' AFTER remark,
    ADD COLUMN vat_invoice_number VARCHAR(20) NULL COMMENT '税务发票号码' AFTER vat_invoice_code,
    ADD COLUMN invoice_kind VARCHAR(32) NOT NULL DEFAULT 'ORDINARY_VAT' COMMENT 'ORDINARY_VAT普票 SPECIAL_VAT专票' AFTER vat_invoice_number;

ALTER TABLE reconciliation
    ADD COLUMN supplier_confirmed_at TIMESTAMP(6) NULL COMMENT '供应商确认时间' AFTER remark,
    ADD COLUMN procurement_confirmed_at TIMESTAMP(6) NULL COMMENT '采购确认时间' AFTER supplier_confirmed_at;

-- 历史数据：草稿/争议 → 待供应商确认；已确认单补采购确认时间
UPDATE reconciliation SET status = 'PENDING_SUPPLIER' WHERE status IN ('DRAFT', 'DISPUTED');
UPDATE reconciliation SET procurement_confirmed_at = updated_at WHERE status = 'CONFIRMED' AND procurement_confirmed_at IS NULL;
