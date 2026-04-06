-- T7: RBAC 增强 + 审计日志

SET @db := DATABASE();

-- 1) sys_user 补充 supplier_id 列（门户用户关联供应商）
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'supplier_id') > 0,
        'SELECT 1',
        'ALTER TABLE sys_user ADD COLUMN supplier_id BIGINT, ADD CONSTRAINT fk_user_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id BIGINT,
    detail VARCHAR(2000),
    ip_address VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 种子角色（幂等）
INSERT IGNORE INTO sys_role (code, name, created_at, updated_at) VALUES
    ('ADMIN', '系统管理员', NOW(6), NOW(6)),
    ('BUYER', '采购员', NOW(6), NOW(6)),
    ('BUYER_MANAGER', '采购主管', NOW(6), NOW(6)),
    ('WAREHOUSE', '仓管员', NOW(6), NOW(6)),
    ('SUPPLIER', '供应商用户', NOW(6), NOW(6));

-- 注意：admin 用户由 Java SeedDataInitializer 在启动时创建（使用 PasswordEncoder）。
