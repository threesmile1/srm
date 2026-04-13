# SRM 部署说明

本文说明如何在服务器或内网环境中部署 **srm-backend**（Spring Boot）、**srm-admin-web**（管理端）、**srm-portal-web**（供应商门户）。仓库为 Monorepo，根目录：<https://github.com/threesmile1/srm>。

---

## 1. 架构与端口

| 组件 | 技术栈 | 默认端口（开发） | 说明 |
|------|--------|------------------|------|
| 后端 | Java 17、Spring Boot 3、MySQL、Flyway | **8080** | REST API、`/api/*` |
| 管理端 | Vue 3、Vite | **5173** | 开发时通过 Vite 将 `/api` 代理到后端 |
| 门户 | Vue 3、Vite | **5174** | 生产环境需配置与后端通信的 API 基地址 |

生产环境通常由 **Nginx（或其它反向代理）** 对外提供 **HTTPS**，静态资源走 80/443，API 路径转发到内网 `8080`。

---

## 2. 环境要求

- **操作系统**：Linux x64 或 Windows Server（文档以 Linux 命令为主，Windows 可对照使用 PowerShell）。
- **JDK**：**17+**（与 `srm-backend/pom.xml` 一致）。
- **Maven**：**3.9+**（仅构建机需要；亦可在 CI 中构建后只部署 JAR）。
- **Node.js**：**20 LTS 或 22**（推荐），用于前端 `npm run build`。
- **MySQL**：**8.0**，字符集建议 **utf8mb4**。
- **网络**：构建与运行环境能访问 Maven Central / npm registry（或已配置私服镜像）。

---

## 3. 数据库（MySQL）

### 3.1 创建库与用户

1. 在目标 MySQL 上创建数据库与用户，与后端连接串一致。可参考仓库内脚本：

   `srm-backend/scripts/init-local-mysql.sql`

2. 典型 JDBC URL 形式（见 `srm-backend/src/main/resources/application.yml`）：

   `jdbc:mysql://<主机>:3306/srm?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai`

3. **时区**：业务时区为 **Asia/Shanghai**（配置项 `srm.business-timezone`），数据库与 JVM 时区建议统一为东八区或明确使用 `serverTimezone=Asia/Shanghai`。

### 3.2 迁移（Flyway）

- 后端启动时会自动执行 `classpath:db/migration` 下的脚本（`spring.flyway.enabled=true`）。
- **开发环境**若启用 **`dev`** Profile，存在 **DevFlywayRepairConfig**（先 `repair` 再 `migrate`），便于处理 checksum 变更。
- **生产环境建议不要使用 `dev` Profile**，以免执行仅用于开发的种子数据逻辑（见 `DevDataBootstrap`）。应通过 **独立配置** 覆盖数据源与 Profile（见下文）。

### 3.3 备份

上线与升级前请对 `srm` 库做 **逻辑备份**（`mysqldump`）或按公司规范做物理备份。

---

## 4. 后端部署（srm-backend）

### 4.1 构建

```bash
cd srm-backend
mvn clean package -DskipTests
```

产物：`srm-backend/target/srm-backend-0.1.0-SNAPSHOT.jar`（版本以 `pom.xml` 为准）。

### 4.2 运行示例

```bash
java -jar target/srm-backend-0.1.0-SNAPSHOT.jar
```

默认 `application.yml` 中 `spring.profiles.active` 为 **dev**。生产环境请通过 **环境变量或外部配置文件** 覆盖，例如：

- **`SPRING_PROFILES_ACTIVE`**：设为 **`prod`**（仓库已提供 `srm-backend/src/main/resources/application-prod.yml`），或 **不包含 `dev`**，避免开发种子与 Dev Flyway 修复逻辑；亦可使用 **Spring Boot 外部配置**（`spring.config.additional-location` 指向仅服务器可读目录）覆盖敏感项。
- **`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`**：生产数据库连接。
- **`SRM_INVOICE_UPLOAD_DIR`**：发票附件目录（对应 `srm.invoice-upload-dir`，默认 `~/.srm/invoice-files`）。请改为服务器上的持久化目录并配置备份与权限。

其它常用项见 `application.yml` 中 **`srm.*`**（如 U9/帆软地址、超时、上传大小等）。

### 4.3 systemd 示例（可选）

将 `WorkingDirectory`、`ExecStart`、`User` 按实际路径与用户修改：

```ini
[Unit]
Description=SRM Backend
After=network.target mysql.service

[Service]
Type=simple
User=srm
WorkingDirectory=/opt/srm/srm-backend
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/srm?...
Environment=SPRING_DATASOURCE_USERNAME=srm
Environment=SPRING_DATASOURCE_PASSWORD=********
Environment=SRM_INVOICE_UPLOAD_DIR=/var/srm/invoice-files
ExecStart=/usr/bin/java -jar /opt/srm/srm-backend/target/srm-backend-0.1.0-SNAPSHOT.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### 4.4 健康与文档

- 健康检查：`GET /api/v1/public/ping`
- OpenAPI：`/swagger-ui.html`（生产是否暴露请结合安全策略与网关限制）

---

## 5. 前端构建与发布

### 5.1 管理端（srm-admin-web）

```bash
cd srm-admin-web
npm ci
npm run build
```

产物一般在 `dist/`。开发时通过 `vite.config.ts` 将 **`/api`** 代理到后端；**生产环境**若静态站点与 API **不同源**，需设置构建期环境变量 **`VITE_API_BASE`** 为对外 API 根地址（例如 `https://srm.example.com` 或 `https://api.example.com`），与 `src/api/http.ts` 逻辑一致。

若 Nginx **同域反代**（浏览器访问 `https://srm.example.com`，其中 `/api` 转发到后端），可将前端 **`baseURL` 置为空字符串**（同源 `/api`），此时需在构建时使用与线上一致的 `VITE_API_BASE` 或按项目约定留空（参见各环境 `.env` 约定）。

### 5.2 门户（srm-portal-web）

```bash
cd srm-portal-web
npm ci
npm run build
```

门户默认在开发环境通过 **`VITE_API_BASE`** 指向后端（见 `.env.development`）。生产构建务必设置 **`VITE_API_BASE`** 为线上 API 根地址。

---

## 6. 反向代理示例（Nginx）

以下仅为示例：将静态资源与 API 统一到同一域名，由 Nginx 转发 `/api` 到 Spring Boot。

```nginx
server {
    listen 443 ssl;
    server_name srm.example.com;

    # ssl_certificate / ssl_certificate_key ...

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 20m;
    }

    location / {
        root /var/www/srm-admin;
        try_files $uri $uri/ /index.html;
    }
}
```

门户若使用独立域名，可再建一个 `server` 块，`root` 指向门户的 `dist`。**CORS**：若前后端不同源且浏览器直连 API，需在 Spring Security 中放行对应域；**推荐**同域反代以减少跨域配置。

---

## 7. 配置与安全摘要

- **密钥**：数据库密码、生产 `application-*.yml` **勿提交 Git**；使用环境变量或密钥管理。
- **栈信息**：默认 `srm.api.include-stack-trace=false`；`dev` 下为 `true`（见 `application-dev.yml`）。
- **文件上传**：发票等附件目录 `srm.invoice-upload-dir` 需磁盘空间与定期备份。
- **U9/帆软**：`srm.u9.*` 在生产环境改为实际内网或专线可达地址，并控制超时与重试。

---

## 8. 验收清单

- [ ] MySQL 可连接，Flyway 迁移无报错。
- [ ] `GET /api/v1/public/ping` 返回正常。
- [ ] 管理端页面可登录、主要菜单可打开（按权限）。
- [ ] 门户可访问，接口可拉取数据（注意供应商身份与认证头/会话）。
- [ ] 上传类功能（如发票附件）写入目录可读写且路径正确。

---

## 9. 升级与回滚

1. **升级**：拉取新版本 → 备份数据库 → 部署新 JAR / 新静态资源 → 重启后端（Flyway 自动升级 schema）。
2. **回滚**：恢复上一版 JAR 与前端静态文件；若已执行不可回退的迁移，需按 DBA 流程处理库结构，**勿**随意删除 Flyway 历史表记录。

---

## 10. Docker 与 Compose

### 10.1 可选：仅起 MySQL（开发/试验）

`srm-backend/docker-compose.yml` 可一键启动 **MySQL 8** 容器（默认映射主机 **3306**）。若宿主机已有其它 MySQL 或需避免端口冲突，请改 `ports` 映射。**团队本地联调以本机安装 MySQL 为规范时，生产数据库仍建议按公司规范选用独立实例**；此文件适合个人试验或临时起库。

### 10.2 生产：后端 + MySQL（`docker-compose.prod.yml`）

仓库根目录提供 **`docker-compose.prod.yml`**、**`srm-backend/Dockerfile`** 与 **`.env.example`**，用于在 **Docker + Compose** 下一同运行 **MySQL 8** 与 **srm-backend**（多阶段构建 JAR，运行时使用 **`prod`** Profile，无开发种子）。

**固定端口（仅绑定本机回环，由宿主机 Nginx 反代对外）：**

| 用途 | 主机地址 | 说明 |
|------|----------|------|
| HTTP API | `127.0.0.1:18081` | 容器内 8080；**第 6 节 Nginx 示例**中 `proxy_pass` 应指向此处而非 `8080`（若与其它项目冲突，可同时改 Compose 与 Nginx） |
| MySQL | `127.0.0.1:13306` | 映射到容器 3306；与宿主机其它 MySQL（如 5.7 占用的 3306）隔离 |

**对外访问（Nginx 监听本机 `80` 的典型做法）：** 路由器可将 **WAN:8888 → LAN:本机:80**，浏览器地址栏为 **`http://srm.hjlg.in:8888`**（等），实际打到本机 **Nginx 的 `80`**。按域名分流示例：**`srm.hjlg.in`** → 采购管理端静态资源；**`srm1.hjlg.in`** → 供应商门户静态资源；二者均将 **`location /api/`** 反代到 **`http://127.0.0.1:18081`**。构建时 **`VITE_API_BASE`** 须与浏览器实际访问的**完整源**一致（含协议、主机名、端口），例如管理端 **`http://srm.hjlg.in:8888`**、门户 **`http://srm1.hjlg.in:8888`**。若 **`80` 被其它程序占用**（如宝塔占 `8888`），需调整监听端口或改用面板反代，并同步修改构建参数。

**步骤摘要：**

1. 安装 **Docker** 与 **Compose**（`docker compose` 或 `docker-compose` 均可）。
2. 在**仓库根目录**复制环境文件并填写强密码：  
   `cp .env.example .env`  
   至少设置 **`MYSQL_ROOT_PASSWORD`**（运维/备份）与 **`SRM_DB_PASSWORD`**（应用用户 `srm`，须与 Compose 中 MySQL 服务一致）。
3. 构建并后台启动：  
   `docker compose -f docker-compose.prod.yml --env-file .env up -d --build`  
   若使用旧版 CLI，可指定项目名以免卷名混乱：  
   `docker-compose -p srm -f docker-compose.prod.yml --env-file .env up -d --build`
4. **Nginx**：为 SRM 增加独立 `server_name`，将 **`location /api/`**（路径以前缀为准，与现有站点一致即可）反代到 **`http://127.0.0.1:18081`**，并配置 `proxy_set_header Host / X-Forwarded-*`、`client_max_body_size` 等与第 6 节一致。
5. **前端**：分别构建。**管理端** `srm-admin-web`：`VITE_API_BASE=http://srm.hjlg.in:8888 npm run build`（端口与路由器/HTTPS 实际一致即可）。**门户** `srm-portal-web`：`VITE_API_BASE=http://srm1.hjlg.in:8888 npm run build`。若采用**同域**反代且浏览器与 API 同源，可按项目约定将 `VITE_API_BASE` 留空或设为与线上一致（须避免生产构建落到默认的 `localhost:8080`，见各前端 `src/api/http.ts`）。
6. **验证**：`curl -sS http://127.0.0.1:18081/api/v1/public/ping`；浏览器经域名访问登录与菜单（见第 8 节清单）。

**数据持久化：** Compose 使用命名卷 **`srm-mysql-data`**（库文件）、**`srm-invoice-files`**（发票附件，对应环境变量 **`SRM_INVOICE_UPLOAD_DIR=/data/invoice-files`**）。升级前请备份数据库与附件目录策略。

**使用 Navicat / 客户端连库：** Compose 将 MySQL 映射为 **`0.0.0.0:13306`** 时，局域网机器可连 **`<服务器局域网 IP>:13306`**（示例：本机 **`192.168.11.6`**，以 `ip -4 addr` 为准）。**UFW** 建议仅放行 **`192.168.11.0/24`** 访问 **13306/tcp**，勿对公网暴露。账号 **`srm`** / 库 **`srm`**，密码见 **`.env` 中 `SRM_DB_PASSWORD`**。仅本机连接仍可用 **`127.0.0.1:13306`**；外网请优先 **SSH 隧道**。

**说明：** `.env` 已列入 `.gitignore`，勿将真实密码提交仓库。

---

## 11. 相关文档

- 根目录 [README.md](../README.md)：仓库结构与本机启动摘要。
- [srm-backend/README.md](../srm-backend/README.md)：后端详细说明、故障排除、模块列表。
- 双域名 Nginx 示例（`srm.hjlg.in` / `srm1.hjlg.in`）：[nginx-srm-hjlg.in.example.conf](./nginx-srm-hjlg.in.example.conf)。
