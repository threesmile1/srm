-- 宁波公司收货单：客服审核（业务状态 + 审批规则 + 角色）

-- 1) 收货单业务状态（与 Java GrStatus 一致）
ALTER TABLE goods_receipt
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'APPROVED' COMMENT '收货单业务状态(PENDING_APPROVAL/APPROVED/REJECTED)' AFTER export_status;

-- 2) 客服角色（审批待办按角色匹配）
INSERT IGNORE INTO sys_role (code, name, created_at, updated_at) VALUES
    ('CUSTOMER_SERVICE', '客服', NOW(6), NOW(6));

-- 3) 收货单审批：客服一级（金额区间覆盖全部）
INSERT IGNORE INTO approval_rule (doc_type, min_amount, max_amount, approval_level, approver_role, description, enabled, created_at, updated_at)
VALUES
    ('GR', 0, NULL, 1, 'CUSTOMER_SERVICE', '收货单（宁波公司）客服审核', 1, NOW(6), NOW(6));
