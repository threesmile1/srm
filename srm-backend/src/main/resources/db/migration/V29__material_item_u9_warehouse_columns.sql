-- 与 U9 字段命名一致：四厂仓加 u9_ 前缀；删除已由分厂仓替代的 u9_warehouse_name
ALTER TABLE material_item DROP COLUMN u9_warehouse_name;

ALTER TABLE material_item
    CHANGE COLUMN warehouse_suzhou u9_warehouse_suzhou VARCHAR(255) NULL COMMENT '苏州工厂存储仓库(U9同步)',
    CHANGE COLUMN warehouse_chengdu u9_warehouse_chengdu VARCHAR(255) NULL COMMENT '成都工厂存储仓库(U9同步)',
    CHANGE COLUMN warehouse_huanan u9_warehouse_huanan VARCHAR(255) NULL COMMENT '华南工厂存储仓库(U9同步)',
    CHANGE COLUMN warehouse_shuiqi u9_warehouse_shuiqi VARCHAR(255) NULL COMMENT '水漆工厂存储仓库(U9同步)';
