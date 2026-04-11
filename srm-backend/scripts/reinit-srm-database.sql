-- 丢弃并重建空库 `srm`。表结构由 Flyway 在下次启动后端时自动迁移；dev 下 DevDataBootstrap 会灌演示数据。
-- 需要具备对库 `srm` 的 DROP 以及建库权限（一般用 root）：mysql -u root -p < scripts/reinit-srm-database.sql
-- 若仅用应用账号（通常只有 srm.*），可能无法执行 DROP DATABASE，请换 root 或让 DBA 授权。

DROP DATABASE IF EXISTS srm;

CREATE DATABASE srm
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
