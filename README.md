# SRM 项目工作区

本目录聚合 **文档** 与三个 **分仓** 工程占位，便于本地联调。生产环境可对三工程使用 **三个独立 Git 远程仓库**。

| 路径 | 说明 |
|------|------|
| [docs/SRM建设方案.md](./docs/SRM建设方案.md) | 业务与架构方案 |
| [docs/开发计划.md](./docs/开发计划.md) | 阶段与 Sprint 参考 |
| [srm-backend](./srm-backend/) | Java / Spring Boot 3 后端（**一期 A1 API 已脚手架**） |
| [srm-admin-web](./srm-admin-web/) | Vue 3 + TS 管理端（**5173**，联调页） |
| [srm-portal-web](./srm-portal-web/) | Vue 3 + TS 供应商门户（**5174**，联调页） |

联调时：后端提供 OpenAPI；两前端分别配置 `VITE_API_BASE_URL`（或等价变量）指向同一后端地址。

## 分仓 Git（各子目录独立仓库）

三个子目录已可 **各自** 绑定远程（示例仓库名请换成你的）：

```powershell
# 后端
cd srm-backend
git add README.md .gitignore
git commit -m "chore: init repository"
git branch -M main
git remote add origin https://github.com/你的组织/srm-backend.git
git push -u origin main

# 管理端
cd ..\srm-admin-web
git add README.md .gitignore
git commit -m "chore: init repository"
git branch -M main
git remote add origin https://github.com/你的组织/srm-admin-web.git
git push -u origin main

# 门户
cd ..\srm-portal-web
git add README.md .gitignore
git commit -m "chore: init repository"
git branch -M main
git remote add origin https://github.com/你的组织/srm-portal-web.git
git push -u origin main
```

## 父仓库（文档与工作区说明）

上级目录 **已 `git init`**，默认只纳入 **`docs/`**、根目录 **`README.md`**、**`.gitignore`**。  
**`srm-backend` / `srm-admin-web` / `srm-portal-web`** 已在 **`.gitignore` 中排除**，避免与子目录内独立 `.git` 混提交；三工程仍按上文 **各自 `remote` + `push`**。

绑定文档仓远程示例：

```powershell
cd d:\SRM
git remote add origin https://github.com/你的组织/srm-docs.git
git push -u origin main
```

在 GitHub/GitLab **先创建空仓库**（三子仓 + 可选文档仓）；子仓推送时不要勾选自动 README，避免首次 push 冲突。
