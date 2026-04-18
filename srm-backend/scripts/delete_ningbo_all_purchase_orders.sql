-- 危险：删除「宁波」采购组织下全部采购订单及 ASN / 收货 / 审批 / 发票行 / 质检等关联数据（一次性运维）
-- 组织判定：org_unit.org_type = 'PROCUREMENT' AND code = 'NB'
-- 用法示例：
--   mysql -u USER -p srm < scripts/delete_ningbo_all_purchase_orders.sql
-- 若还需清空宁波组织下发票头/对账单：delete_ningbo_invoice_and_reconciliation.sql
-- 若还需清空宁波请购单：delete_ningbo_purchase_requisitions.sql

SET @nb_org := (SELECT id FROM org_unit WHERE org_type = 'PROCUREMENT' AND code = 'NB' LIMIT 1);

SELECT IF(@nb_org IS NULL,
       'ERROR: NB procurement org not found (org_unit code=NB)',
       CONCAT('Will delete all PO under procurement_org_id=', @nb_org)) AS status_check;

-- 以下在 @nb_org 为空时不删数据

-- 1) 发票行
DELETE il FROM invoice_line il
INNER JOIN purchase_order po ON il.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE il FROM invoice_line il
INNER JOIN purchase_order_line pol ON il.purchase_order_line_id = pol.id
INNER JOIN purchase_order po ON pol.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE il FROM invoice_line il
INNER JOIN goods_receipt gr ON il.goods_receipt_id = gr.id
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 2) 质量整改（依赖质检）
DELETE ca FROM corrective_action ca
INNER JOIN quality_inspection qi ON ca.inspection_id = qi.id
INNER JOIN goods_receipt gr ON qi.goods_receipt_id = gr.id
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 3) 质检
DELETE qi FROM quality_inspection qi
INNER JOIN goods_receipt gr ON qi.goods_receipt_id = gr.id
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 4) 站内通知
DELETE n FROM notification n
INNER JOIN goods_receipt gr ON n.ref_type = 'GR' AND n.ref_id = gr.id
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE n FROM notification n
INNER JOIN asn_notice asn ON n.ref_type = 'ASN' AND n.ref_id = asn.id
INNER JOIN purchase_order po ON asn.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE n FROM notification n
INNER JOIN purchase_order po ON n.ref_type = 'PO' AND n.ref_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 5) 审批实例（PO / ASN / GR）
DELETE ai FROM approval_instance ai
INNER JOIN purchase_order po ON ai.doc_type = 'PO' AND ai.doc_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE ai FROM approval_instance ai
INNER JOIN asn_notice asn ON ai.doc_type = 'ASN' AND ai.doc_id = asn.id
INNER JOIN purchase_order po ON asn.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

DELETE ai FROM approval_instance ai
INNER JOIN goods_receipt gr ON ai.doc_type = 'GR' AND ai.doc_id = gr.id
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 6) 收货单（行随 GR CASCADE）
DELETE gr FROM goods_receipt gr
INNER JOIN purchase_order po ON gr.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 7) 发货通知（行随 ASN CASCADE）
DELETE asn FROM asn_notice asn
INNER JOIN purchase_order po ON asn.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 8) 请购行转单引用
UPDATE purchase_requisition_line prl
INNER JOIN purchase_order po ON prl.converted_po_id = po.id
SET prl.converted_po_id = NULL
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 9) 订单行
DELETE pol FROM purchase_order_line pol
INNER JOIN purchase_order po ON pol.purchase_order_id = po.id
WHERE @nb_org IS NOT NULL AND po.procurement_org_id = @nb_org;

-- 10) 订单头
DELETE FROM purchase_order WHERE @nb_org IS NOT NULL AND procurement_org_id = @nb_org;

SELECT CONCAT('Done. Removed all purchase_order for NB org id=', IFNULL(@nb_org, 'NULL')) AS result;
