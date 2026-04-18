-- 宁波：ASN 客服确认记录（不阻断收货） + U9 收货单状态门禁字段

SET @db := DATABASE();

-- 1) ASN：客服确认记录字段
-- asn_notice：兼容重复执行/已有列的场景（使用 information_schema 判断）
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'cs_confirm_status') > 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE asn_notice ',
            'ADD COLUMN cs_confirm_status VARCHAR(32) NULL COMMENT ''客服确认状态(CONFIRMED/REJECTED)'' AFTER status, ',
            'ADD COLUMN cs_confirmer_id BIGINT NULL AFTER cs_confirm_status, ',
            'ADD COLUMN cs_confirmer_name VARCHAR(128) NULL AFTER cs_confirmer_id, ',
            'ADD COLUMN cs_confirmed_at TIMESTAMP(6) NULL AFTER cs_confirmer_name, ',
            'ADD COLUMN cs_confirm_comment VARCHAR(1000) NULL AFTER cs_confirmed_at'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 审批规则：ASN → 客服（仅用于宁波创建时发起审批，审批结果写回确认记录）
INSERT IGNORE INTO approval_rule (doc_type, min_amount, max_amount, approval_level, approver_role, description, enabled, created_at, updated_at)
VALUES
    ('ASN', 0, NULL, 1, 'CUSTOMER_SERVICE', '发货通知（宁波公司）客服确认记录', 1, NOW(6), NOW(6));

-- 3) 收货单：U9 同步字段（用于宁波「仅业务关闭可开票/对账」）
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'goods_receipt' AND COLUMN_NAME = 'u9_status') > 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE goods_receipt ',
            'ADD COLUMN source_system VARCHAR(32) NULL AFTER status, ',
            'ADD COLUMN u9_doc_no VARCHAR(64) NULL AFTER source_system, ',
            'ADD COLUMN u9_status VARCHAR(64) NULL AFTER u9_doc_no'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) U9 收货单幂等索引（同组织内唯一）
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'goods_receipt' AND INDEX_NAME = 'uk_gr_org_u9_doc_no') > 0,
        'SELECT 1',
        'CREATE UNIQUE INDEX uk_gr_org_u9_doc_no ON goods_receipt(procurement_org_id, u9_doc_no)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

