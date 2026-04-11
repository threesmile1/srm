-- 开发库：删除全部供应商主数据及其关联业务单据（保留账套/组织/仓库/物料/管理员等基础数据）。
-- 重启后端后 DevDataBootstrap 会在 supplier 为空时重新种子演示供应商与 portal 用户。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE sys_user SET supplier_id = NULL WHERE supplier_id IS NOT NULL;

DELETE FROM supplier_org_scope;

DELETE FROM reconciliation_line;
DELETE FROM invoice_attachment;
DELETE FROM invoice_line;
DELETE FROM invoice;
DELETE FROM reconciliation;

DELETE FROM perf_score;
DELETE FROM perf_evaluation;

DELETE FROM rfq_quotation_line;
DELETE FROM rfq_quotation;
DELETE FROM rfq_invitation;
DELETE FROM rfq_line;
DELETE FROM rfq;

DELETE FROM contract_line;
DELETE FROM contract;

DELETE FROM supplier_audit;

DELETE FROM corrective_action;
DELETE FROM quality_inspection;

DELETE FROM notification WHERE recipient_supplier_id IS NOT NULL;

DELETE FROM goods_receipt_line;
DELETE FROM goods_receipt;

DELETE FROM asn_line;
DELETE FROM asn_notice;

DELETE FROM purchase_order_line;
DELETE FROM purchase_order;

UPDATE purchase_requisition_line SET supplier_id = NULL, converted_po_id = NULL;

DELETE FROM approval_step;
DELETE FROM approval_instance;

DELETE FROM supplier;

SET FOREIGN_KEY_CHECKS = 1;
