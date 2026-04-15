-- A5 ASN：表头增加收货信息 + 物流单附件
SET @db := DATABASE();

-- 收货人
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'receiver_name') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN receiver_name VARCHAR(255)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 收货人联系方式
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'receiver_phone') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN receiver_phone VARCHAR(128)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 收货地址
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'receiver_address') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN receiver_address VARCHAR(1000)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 物流单附件元数据（单文件）
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'logistics_attachment_original_name') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN logistics_attachment_original_name VARCHAR(500)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'logistics_attachment_content_type') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN logistics_attachment_content_type VARCHAR(200)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'logistics_attachment_file_size') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN logistics_attachment_file_size BIGINT'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_notice' AND COLUMN_NAME = 'logistics_attachment_stored_path') > 0,
        'SELECT 1',
        'ALTER TABLE asn_notice ADD COLUMN logistics_attachment_stored_path VARCHAR(1000)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

