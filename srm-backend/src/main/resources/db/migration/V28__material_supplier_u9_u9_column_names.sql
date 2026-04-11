-- 与 material_item 对齐：多供应商表使用 u9_supplier_code / u9_supplier_name
ALTER TABLE material_supplier_u9 DROP INDEX uk_msu9_mat_sup;

ALTER TABLE material_supplier_u9
    CHANGE COLUMN supplier_code u9_supplier_code VARCHAR(64) NOT NULL COMMENT 'U9 供应商编码',
    CHANGE COLUMN supplier_name u9_supplier_name VARCHAR(255) NULL COMMENT 'U9 供应商名称';

ALTER TABLE material_supplier_u9
    ADD UNIQUE KEY uk_msu9_mat_sup (material_id, u9_supplier_code);
