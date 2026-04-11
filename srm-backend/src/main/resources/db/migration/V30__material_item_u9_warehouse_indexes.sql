-- 仓库列表按页统计物料引用：四厂仓列等值/范围扫描时可走索引（配合应用侧先分页仓库再统计）
CREATE INDEX idx_material_item_u9_wh_suzhou ON material_item (u9_warehouse_suzhou);
CREATE INDEX idx_material_item_u9_wh_chengdu ON material_item (u9_warehouse_chengdu);
CREATE INDEX idx_material_item_u9_wh_huanan ON material_item (u9_warehouse_huanan);
CREATE INDEX idx_material_item_u9_wh_shuiqi ON material_item (u9_warehouse_shuiqi);
