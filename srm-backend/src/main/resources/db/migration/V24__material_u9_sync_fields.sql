-- U9 物料同步扩展：规格、参考价、仓库名、供应商快照（wuliao.cpt 等接口落地字段）
ALTER TABLE material_item ADD COLUMN specification VARCHAR(512) NULL COMMENT '规格型号';
ALTER TABLE material_item ADD COLUMN purchase_unit_price DECIMAL(19, 4) NULL COMMENT '参考采购单价(U9同步)';
ALTER TABLE material_item ADD COLUMN u9_warehouse_name VARCHAR(255) NULL COMMENT 'U9同步仓库名称';
ALTER TABLE material_item ADD COLUMN u9_supplier_code VARCHAR(64) NULL COMMENT 'U9同步供应商编码';
ALTER TABLE material_item ADD COLUMN u9_supplier_name VARCHAR(255) NULL COMMENT 'U9同步供应商名称';
