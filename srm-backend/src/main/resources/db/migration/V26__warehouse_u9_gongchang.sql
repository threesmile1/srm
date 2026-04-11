-- U9 仓库报表 cangku.cpt：工厂/品类（gongchang）展示与检索

ALTER TABLE warehouse
    ADD COLUMN u9_gongchang VARCHAR(255) NULL COMMENT 'U9同步工厂/品类(gongchang)' AFTER name;
