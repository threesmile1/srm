-- U9 采购执行报表展示字段（来自 caigou_cp.cpt / U9 SQL 别名）
ALTER TABLE purchase_order
    ADD COLUMN u9_business_date DATE NULL COMMENT 'U9 业务日期' AFTER u9_doc_no,
    ADD COLUMN u9_official_order_no VARCHAR(128) NULL COMMENT '正式订单号（DescFlexField_PrivateDescSeg5）' AFTER u9_business_date,
    ADD COLUMN u9_store2 VARCHAR(128) NULL COMMENT '二级门店（DescFlexField_PrivateDescSeg8）' AFTER u9_official_order_no,
    ADD COLUMN u9_receiver_name VARCHAR(128) NULL COMMENT '收货人名称（DescFlexField_PrivateDescSeg3）' AFTER u9_store2,
    ADD COLUMN u9_terminal_phone VARCHAR(64) NULL COMMENT '终端电话（DescFlexField_PrivateDescSeg11）' AFTER u9_receiver_name,
    ADD COLUMN u9_install_address VARCHAR(512) NULL COMMENT '安装地址（DescFlexField_PrivateDescSeg10）' AFTER u9_terminal_phone;

