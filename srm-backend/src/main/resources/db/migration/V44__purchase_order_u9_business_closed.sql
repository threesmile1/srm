-- 宁波：U9 采购订单增加「业务是否关闭」字段（1=业务关闭）

SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'u9_business_closed') > 0,
        'SELECT 1',
        'ALTER TABLE purchase_order ADD COLUMN u9_business_closed TINYINT(1) NULL COMMENT ''U9 业务是否关闭(1=关闭)'' AFTER u9_install_address'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

