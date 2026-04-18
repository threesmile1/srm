-- 按订单号级联删除采购订单及 ASN / 收货 / 审批 / 发票行 / 质检等关联数据（一次性运维脚本）
-- 用法: "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u srm -p srm < scripts/delete_purchase_order_cascade.sql
-- 修改下方 SET @po_no 为目标订单号

-- 执行前必须改为目标订单号（勿留占位符）
SET @po_no := 'REPLACE_WITH_PO_NO';
SET @po_id := NULL;

SELECT id INTO @po_id FROM purchase_order WHERE po_no = @po_no LIMIT 1;

SELECT IF(@po_id IS NULL, CONCAT('ERROR: purchase_order not found: ', @po_no), CONCAT('Deleting PO id=', @po_id)) AS status_check;

-- 以下在 @po_id 为空时各语句因条件不匹配而不删数据

-- 1) 发票行
DELETE il FROM invoice_line il WHERE @po_id IS NOT NULL AND il.purchase_order_id = @po_id;

DELETE il FROM invoice_line il
INNER JOIN purchase_order_line pol ON il.purchase_order_line_id = pol.id
WHERE @po_id IS NOT NULL AND pol.purchase_order_id = @po_id;

DELETE il FROM invoice_line il
INNER JOIN goods_receipt gr ON il.goods_receipt_id = gr.id
WHERE @po_id IS NOT NULL AND gr.purchase_order_id = @po_id;

-- 2) 质量整改（依赖质检）
DELETE ca FROM corrective_action ca
INNER JOIN quality_inspection qi ON ca.inspection_id = qi.id
INNER JOIN goods_receipt gr ON qi.goods_receipt_id = gr.id
WHERE @po_id IS NOT NULL AND gr.purchase_order_id = @po_id;

-- 3) 质检
DELETE qi FROM quality_inspection qi
INNER JOIN goods_receipt gr ON qi.goods_receipt_id = gr.id
WHERE @po_id IS NOT NULL AND gr.purchase_order_id = @po_id;

-- 4) 站内通知
DELETE n FROM notification n
INNER JOIN goods_receipt gr ON n.ref_type = 'GR' AND n.ref_id = gr.id
WHERE @po_id IS NOT NULL AND gr.purchase_order_id = @po_id;

DELETE n FROM notification n
INNER JOIN asn_notice asn ON n.ref_type = 'ASN' AND n.ref_id = asn.id
WHERE @po_id IS NOT NULL AND asn.purchase_order_id = @po_id;

DELETE FROM notification WHERE @po_id IS NOT NULL AND ref_type = 'PO' AND ref_id = @po_id;

-- 5) 审批实例（PO / ASN / GR）
DELETE FROM approval_instance WHERE @po_id IS NOT NULL AND doc_type = 'PO' AND doc_id = @po_id;

DELETE ai FROM approval_instance ai
INNER JOIN asn_notice asn ON ai.doc_type = 'ASN' AND ai.doc_id = asn.id
WHERE @po_id IS NOT NULL AND asn.purchase_order_id = @po_id;

DELETE ai FROM approval_instance ai
INNER JOIN goods_receipt gr ON ai.doc_type = 'GR' AND ai.doc_id = gr.id
WHERE @po_id IS NOT NULL AND gr.purchase_order_id = @po_id;

-- 6) 收货单（行 CASCADE）
DELETE FROM goods_receipt WHERE @po_id IS NOT NULL AND purchase_order_id = @po_id;

-- 7) 发货通知（行 CASCADE）
DELETE FROM asn_notice WHERE @po_id IS NOT NULL AND purchase_order_id = @po_id;

-- 8) 请购行转单引用
UPDATE purchase_requisition_line SET converted_po_id = NULL WHERE @po_id IS NOT NULL AND converted_po_id = @po_id;

-- 9) 订单行
DELETE FROM purchase_order_line WHERE @po_id IS NOT NULL AND purchase_order_id = @po_id;

-- 10) 订单头
DELETE FROM purchase_order WHERE @po_id IS NOT NULL AND id = @po_id;

SELECT CONCAT('Done. Removed po_no=', @po_no) AS result;
