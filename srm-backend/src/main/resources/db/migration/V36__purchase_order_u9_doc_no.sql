-- U9 采购订单同步幂等：按采购组织 + U9 单据编号去重
ALTER TABLE purchase_order
    ADD COLUMN u9_doc_no VARCHAR(128) NULL COMMENT 'U9 采购订单号 PM_PurchaseOrder.DocNo' AFTER remark;

CREATE UNIQUE INDEX uk_po_procurement_u9_doc ON purchase_order (procurement_org_id, u9_doc_no);
