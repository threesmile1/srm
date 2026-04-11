-- U9 物料同步扩展：规格、参考价、仓库名、供应商快照（幂等：列已存在则跳过，兼容已手工建列的库）

SET @db := DATABASE();

-- specification
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'material_item' AND COLUMN_NAME = 'specification');
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE material_item ADD COLUMN specification VARCHAR(512) NULL COMMENT ''规格型号''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- purchase_unit_price
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'material_item' AND COLUMN_NAME = 'purchase_unit_price');
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE material_item ADD COLUMN purchase_unit_price DECIMAL(19, 4) NULL COMMENT ''参考采购单价(U9同步)''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- u9_warehouse_name
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'material_item' AND COLUMN_NAME = 'u9_warehouse_name');
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE material_item ADD COLUMN u9_warehouse_name VARCHAR(255) NULL COMMENT ''U9同步仓库名称''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- u9_supplier_code
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'material_item' AND COLUMN_NAME = 'u9_supplier_code');
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE material_item ADD COLUMN u9_supplier_code VARCHAR(64) NULL COMMENT ''U9同步供应商编码''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- u9_supplier_name
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'material_item' AND COLUMN_NAME = 'u9_supplier_name');
SET @sqlstmt := IF(@exist = 0,
  'ALTER TABLE material_item ADD COLUMN u9_supplier_name VARCHAR(255) NULL COMMENT ''U9同步供应商名称''',
  'SELECT 1');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
