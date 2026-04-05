-- 旧脚本曾使用列名 last_value，与 MySQL 8 窗口函数 LAST_VALUE 冲突；统一改为 seq_value（新库已在 V3/V4 直接使用 seq_value，本脚本仅做升级兼容）。

SET @db := DATABASE();

-- po_number_seq.last_value -> seq_value
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'po_number_seq' AND COLUMN_NAME = 'last_value') > 0,
        'ALTER TABLE po_number_seq CHANGE COLUMN last_value seq_value BIGINT NOT NULL DEFAULT 0',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- gr_number_seq
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'gr_number_seq' AND COLUMN_NAME = 'last_value') > 0,
        'ALTER TABLE gr_number_seq CHANGE COLUMN last_value seq_value BIGINT NOT NULL DEFAULT 0',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- asn_number_seq
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'asn_number_seq' AND COLUMN_NAME = 'last_value') > 0,
        'ALTER TABLE asn_number_seq CHANGE COLUMN last_value seq_value BIGINT NOT NULL DEFAULT 0',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
