-- 采购组织：宁波公司（与 U9 核算组织 AccountOrg 对应，写入 org_unit.u9_org_code）
-- 说明：ledger 以 code=LEDGER01 为准，避免写死 ledger.id。

INSERT INTO org_unit (ledger_id, org_type, code, name, u9_org_code, created_at, updated_at)
SELECT l.id,
       'PROCUREMENT',
       'NB',
       N'宁波公司',
       '1001711275375071',
       NOW(6),
       NOW(6)
FROM ledger l
WHERE l.code = 'LEDGER01'
  AND NOT EXISTS (
        SELECT 1
        FROM org_unit ou
        WHERE ou.ledger_id = l.id
          AND ou.code = 'NB'
      );

-- 主仓库（与其它采购组织种子一致，便于 PO 行 warehouse 非空场景联调）
INSERT INTO warehouse (procurement_org_id, code, name, u9_wh_code, created_at, updated_at)
SELECT ou.id,
       'WH-NB',
       N'主仓库-NB',
       'U9-WH-NB',
       NOW(6),
       NOW(6)
FROM org_unit ou
JOIN ledger l ON l.id = ou.ledger_id
WHERE l.code = 'LEDGER01'
  AND ou.code = 'NB'
  AND NOT EXISTS (
        SELECT 1
        FROM warehouse w
        WHERE w.procurement_org_id = ou.id
          AND w.code = 'WH-NB'
      );
