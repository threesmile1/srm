-- 危险：清空「宁波」采购组织下全部请购单及 PR 审批、通知、审计、单号序列表（一次性运维）
-- 组织：org_unit.org_type = 'PROCUREMENT' AND code = 'NB'
-- purchase_requisition_line 随 purchase_requisition 删除（ON DELETE CASCADE）
-- 用法：
--   mysql -u USER -p srm < scripts/delete_ningbo_purchase_requisitions.sql

SET @nb_org := (SELECT id FROM org_unit WHERE org_type = 'PROCUREMENT' AND code = 'NB' LIMIT 1);

SELECT IF(@nb_org IS NULL,
       'ERROR: NB procurement org not found (org_unit code=NB)',
       CONCAT('Will purge all PR under procurement_org_id=', @nb_org)) AS status_check;

-- 1) 审批（含 approval_step，随 instance CASCADE）
DELETE ai FROM approval_instance ai
INNER JOIN purchase_requisition pr ON ai.doc_type = 'PR' AND ai.doc_id = pr.id
WHERE @nb_org IS NOT NULL AND pr.procurement_org_id = @nb_org;

-- 2) 站内通知
DELETE n FROM notification n
INNER JOIN purchase_requisition pr ON n.ref_type = 'PR' AND n.ref_id = pr.id
WHERE @nb_org IS NOT NULL AND pr.procurement_org_id = @nb_org;

-- 3) 审计日志
DELETE a FROM audit_log a
INNER JOIN purchase_requisition pr ON a.entity_type = 'PR' AND a.entity_id = pr.id
WHERE @nb_org IS NOT NULL AND pr.procurement_org_id = @nb_org;

-- 4) 请购单头（行级联删除）
DELETE FROM purchase_requisition WHERE @nb_org IS NOT NULL AND procurement_org_id = @nb_org;

-- 5) 请购单号序列表（清空后新建 PR 时由应用自动补当前年度 seq=0）
DELETE FROM pr_number_seq WHERE @nb_org IS NOT NULL AND procurement_org_id = @nb_org;

SELECT CONCAT('Done. Purged purchase_requisition for NB org id=', IFNULL(@nb_org, 'NULL')) AS result;
