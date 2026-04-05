-- 本机 MySQL 8：创建库与用户，与 application.yml 默认一致（库名 srm / 用户 srm / 密码 srm）
-- 使用：mysql -u root -p < scripts/init-local-mysql.sql

CREATE DATABASE IF NOT EXISTS srm
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'srm'@'localhost' IDENTIFIED BY 'srm';
GRANT ALL PRIVILEGES ON srm.* TO 'srm'@'localhost';

-- 若应用跑在 WSL/本机其它主机名访问 MySQL，可按需增加：
-- CREATE USER IF NOT EXISTS 'srm'@'%' IDENTIFIED BY 'srm';
-- GRANT ALL PRIVILEGES ON srm.* TO 'srm'@'%';

FLUSH PRIVILEGES;
