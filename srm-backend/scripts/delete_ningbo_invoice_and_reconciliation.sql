-- 危险：清空「宁波」采购组织下的发票、对账及关联通知/审计（一次性运维）
-- 组织：org_unit.org_type = 'PROCUREMENT' AND code = 'NB'
-- invoice / invoice_line / invoice_attachment：随 invoice 级联删除
-- 用法：
--   mysql -u USER -p srm < scripts/delete_ningbo_invoice_and_reconciliation.sql

SET @nb_org := (SELECT id FROM org_unit WHERE org_type = 'PROCUREMENT' AND code = 'NB' LIMIT 1);

SELECT IF(@nb_org IS NULL,
       'ERROR: NB procurement org not found (org_unit code=NB)',
       CONCAT('Will purge invoice & reconciliation for procurement_org_id=', @nb_org)) AS status_check;

-- 1) 站内通知（引用发票/对账）
DELETE n FROM notification n
INNER JOIN invoice inv ON n.ref_type = 'INVOICE' AND n.ref_id = inv.id
WHERE @nb_org IS NOT NULL AND inv.procurement_org_id = @nb_org;

DELETE n FROM notification n
INNER JOIN reconciliation r ON n.ref_type = 'RECONCILIATION' AND n.ref_id = r.id
WHERE @nb_org IS NOT NULL AND r.procurement_org_id = @nb_org;

-- 2) 审计日志（可选但建议一并清理，避免指向已删实体）
DELETE a FROM audit_log a
INNER JOIN invoice inv ON a.entity_type = 'INVOICE' AND a.entity_id = inv.id
WHERE @nb_org IS NOT NULL AND inv.procurement_org_id = @nb_org;

DELETE a FROM audit_log a
INNER JOIN reconciliation r ON a.entity_type = 'RECONCILIATION' AND a.entity_id = r.id
WHERE @nb_org IS NOT NULL AND r.procurement_org_id = @nb_org;

-- 3) 发票（invoice_line、invoice_attachment 随 FK CASCADE）
DELETE FROM invoice WHERE @nb_org IS NOT NULL AND procurement_org_id = @nb_org;

-- 4) 对账单
DELETE FROM reconciliation WHERE @nb_org IS NOT NULL AND procurement_org_id = @nb_org;

SELECT CONCAT('Done. Purged invoice/reconciliation for NB org id=', IFNULL(@nb_org, 'NULL')) AS result;
