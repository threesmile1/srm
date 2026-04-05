# SRM 项目工作区（Monorepo）

本仓库为 **单一 Git 仓库**，聚合文档、`srm-backend`、`srm-admin-web`、`srm-portal-web`。

**远程**：<https://github.com/threesmile1/srm>

| 路径 | 说明 |
|------|------|
| [docs/SRM建设方案.md](./docs/SRM建设方案.md) | 业务与架构方案 |
| [docs/开发计划.md](./docs/开发计划.md) | 阶段与 Sprint 参考 |
| [srm-backend](./srm-backend/) | Java / Spring Boot 3 后端 |
| [srm-admin-web](./srm-admin-web/) | Vue 3 + TS 管理端（**5173**） |
| [srm-portal-web](./srm-portal-web/) | Vue 3 + TS 供应商门户（**5174**） |

联调：后端 OpenAPI；两前端 `VITE_API_BASE` 指向同一后端（如 `http://localhost:8080`）。

## 克隆与推送

```powershell
git clone https://github.com/threesmile1/srm.git
cd srm
# 开发完成后
git add -A
git commit -m "your message"
git push origin main
```

## 本地启动（摘要）

- **MySQL**：本机已装 MySQL 8 时，在 `srm-backend` 执行 `mysql -u root -p < scripts/init-local-mysql.sql` 初始化库与用户；或用 `docker compose up -d` 起容器库。
- 后端：`cd srm-backend` → `mvn spring-boot:run`（JDK 17 + Maven）
- 管理端 / 门户：各目录 `npm install` → `npm run dev`

详见各子目录 `README.md`。
