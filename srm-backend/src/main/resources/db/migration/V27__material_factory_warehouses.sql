-- 物料按工厂维度的默认存储仓库（帆软 cangku_yigui / cangku_shuiqi）
ALTER TABLE material_item
    ADD COLUMN warehouse_suzhou VARCHAR(255) NULL COMMENT '苏州工厂存储仓库' AFTER u9_warehouse_name,
    ADD COLUMN warehouse_chengdu VARCHAR(255) NULL COMMENT '成都工厂存储仓库' AFTER warehouse_suzhou,
    ADD COLUMN warehouse_huanan VARCHAR(255) NULL COMMENT '华南工厂存储仓库' AFTER warehouse_chengdu,
    ADD COLUMN warehouse_shuiqi VARCHAR(255) NULL COMMENT '水漆工厂存储仓库' AFTER warehouse_huanan;
