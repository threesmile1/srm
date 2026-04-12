-- 对账：异议说明、采购驳回原因

ALTER TABLE reconciliation
    ADD COLUMN dispute_reason VARCHAR(1000) NULL COMMENT '异议说明' AFTER procurement_confirmed_at,
    ADD COLUMN disputed_at TIMESTAMP(6) NULL COMMENT '提出异议时间' AFTER dispute_reason,
    ADD COLUMN disputed_by VARCHAR(32) NULL COMMENT '异议方 SUPPLIER | PROCUREMENT' AFTER disputed_at,
    ADD COLUMN procurement_reject_reason VARCHAR(1000) NULL COMMENT '采购驳回原因（最近一次）' AFTER disputed_by;
