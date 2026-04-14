-- 宁波公司默认仓（cangku_yigui.cpt 新增列 cangku_ningbo）
ALTER TABLE material_item
    ADD COLUMN u9_warehouse_ningbo VARCHAR(255) NULL COMMENT '宁波公司存储仓库(U9同步)' AFTER u9_warehouse_shuiqi;

CREATE INDEX idx_material_item_u9_wh_ningbo ON material_item (u9_warehouse_ningbo);

