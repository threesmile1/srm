# srm-backend

SRM **后端**：Java **17**、**Spring Boot 3.4**、**MySQL 8**、**Flyway**、**SpringDoc OpenAPI**。

## 环境要求

- **JDK 17+**、**Maven 3.9+**
- **MySQL 8**：**本机安装并启动**（本地开发的标准方式；**不用 Docker 起库**）。若本机无法安装 MySQL，可选用文末 **Docker（可选）** 或 **H2 内存库（仅快速试跑）**。

### Windows 快速安装（示例）

本机若无 JDK/Maven，可用：

1. **JDK 17**（当前用户）：`winget install Microsoft.OpenJDK.17 -e --accept-package-agreements --accept-source-agreements --scope user`  
   常见路径：`%LOCALAPPDATA%\Programs\Microsoft\jdk-17.*-hotspot`
2. **Maven**：官方压缩包解压到如 `%USERPROFILE%\tools\apache-maven-3.9.6`，将 `bin` 加入 `PATH`；或自行配置 `MAVEN_HOME`。

PowerShell 单次会话示例：

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Microsoft\jdk-17.0.10.7-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.6\bin;$env:PATH"
cd srm-backend
mvn test
```

## 本机 MySQL（推荐，本地默认）

1. 确保 MySQL 服务已启动，且监听 **`localhost:3306`**（或与 `application.yml` 中 URL 一致）。
2. 使用 **`root`**（或其它管理员）执行初始化脚本，创建库与用户（与默认配置一致）：

```powershell
cd srm-backend
mysql -u root -p < scripts/init-local-mysql.sql
```

3. 启动后端（**不要**加 `h2dev` 配置，使用默认 **`dev`** 即可走 **Flyway** + 本机 MySQL）：

```powershell
mvn spring-boot:run
```

若账号/密码与默认不同，可复制 `src/main/resources/application.yml` 为 **`application-local.yml`**（勿提交密钥），仅覆盖 `spring.datasource.*`，并启动：

`mvn spring-boot:run -Dspring-boot.run.profiles=dev,local`

## 用 Docker 启动 MySQL（可选，非本机标准方式）

仅当 **无法在本机安装 MySQL** 且本机已安装 Docker 时，可在 `srm-backend` 目录执行：

```powershell
docker compose up -d
```

默认：`localhost:3306`，库名 `srm`，用户/密码 `srm`/`srm`（与 `application.yml` 一致）。**团队本地联调以本机 MySQL 为准时，请勿依赖此方式。**

## 运行后端

```powershell
cd srm-backend
mvn spring-boot:run
```

- API：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- 健康探测：`GET /api/v1/public/ping`

默认 **`spring.profiles.active=dev`**：

- **仅空库建账套时**：写入账套/组织/仓库 + `admin` / `admin123`。
- **已有账套但无供应商时**：补种子 **供应商 S001、物料、门户用户、已发布演示 PO**（见启动日志中的 `supplierId`）。

> 若自 V1 升级上来且库里 **已有账套无供应商**，需清空 `supplier` 表或整库重建，否则不会触发第二阶段种子。

无本机 MySQL、仅想快速试跑时，可使用内存 H2（**不跑 Flyway**，表由 Hibernate 生成）：`mvn spring-boot:run -Dspring-boot.run.profiles=dev,h2dev`（见 `application-h2dev.yml`）。

生产使用 **`spring.profiles.active=prod`**，关闭 dev 种子并配置数据源与安全策略。

### 故障排除：`No plugin found for prefix 'spring-boot'`

1. 必须在 **`srm-backend`** 目录执行（该目录下有 `pom.xml`）。  
2. 强制拉取依赖并重试：`mvn -U clean spring-boot:run`  
3. 仍报错时用**完整插件坐标**（不依赖前缀解析）：

```powershell
mvn org.springframework.boot:spring-boot-maven-plugin:3.4.2:run
```

4. 使用 **Administrator** 等账户时，本地仓库在 `C:\Users\Administrator\.m2`，需能访问外网下载 `spring-boot-starter-parent`；若公司代理，请配置 `%USERPROFILE%\.m2\settings.xml`。

### 故障排除：Flyway `V3__purchase_order.sql`

- **1064 / `last_value` / `year`**：序列表使用 **`year_val`**（勿用保留字 `year`）；当前序号列使用 **`seq_value`**（勿用 `last_value`，与 MySQL 8 窗口函数 **LAST_VALUE** 冲突）。  
- **1060 / `Duplicate column 'supplier_id'`**：说明 **`supplier_id` 已存在**，脚本又执行了一次。当前 **V3 已改为幂等**（列、外键、表已存在则跳过）；请 **拉取最新 `V3__purchase_order.sql`** 后重启。若 Flyway 里仍有 **失败的 V3 记录**，可先：

```sql
DELETE FROM flyway_schema_history WHERE version = '3' AND success = 0;
```

再 `mvn spring-boot:run`。若 V3 已成功入库但改过脚本导致 **checksum 不一致**，需按 Flyway 文档执行 `repair` 或清空库重来。

### 故障排除：Flyway **Validate failed: migration checksum mismatch**

说明：**库里已记录的脚本校验和** 与 **当前 `db/migration` 文件内容** 不一致（常见于多次修改 V3/V4 等）。

- **开发环境（默认 `dev`）**：**`DevFlywayRepairConfig`**（`@Profile("dev")`）在 Flyway 迁移前执行 **`repair()` 再 `migrate()`**，可自动对齐 `flyway_schema_history` 中的 checksum；一般 **直接重启** `mvn spring-boot:run` 即可。  
- **手动 repair**（不配自动修复时）：在 `srm-backend` 配置好数据源后执行  
  `mvn -Dflyway.configFiles=... flyway:repair`  
  或直接在库里按 [Flyway 文档](https://documentation.red-gate.com/flyway) 调整 `flyway_schema_history`。  
- **仅本地开发库**：也可 `DROP DATABASE srm` 后按 `scripts/init-local-mysql.sql` 重建，再启动（最干净）。

## 已实现模块

| 阶段 | 内容 |
|------|------|
| **A1** | 账套、组织、仓库 REST；用户/角色表；Security 全放行联调 |
| **A2** | 供应商（含采购组织授权）、物料 REST：`/api/v1/master/*` |
| **A3** | 采购订单：创建（自动 PO 号）、审核、发布、取消、关闭 |
| **A4** | 门户 PO 列表/详情、行确认：`/api/v1/portal/*`（联调头 `X-Dev-Supplier-Id` 或 `supplierId` 查询参数） |
| **A5** | ASN：`/api/v1/purchase-orders/{poId}/asn-notices`；门户 `/api/v1/portal/asn-notices` |
| **A6** | 收货单：`/api/v1/goods-receipts`（列表/详情/创建）；累计更新 PO 行 `received_qty` |
| **A7** | U9 用 Excel 导出：`POST /api/v1/exports/purchase-orders`、`/exports/goods-receipts`（`Content-Type` 为 xlsx） |
| **A8** | 报表：`GET /api/v1/reports/purchase-execution?procurementOrgId=` |

Flyway **`V4__asn_and_receipt.sql`**：ASN/GR 表、序号表、PO 行 `received_qty`、PO `export_status` 等。

配置项（`application.yml` / `SrmProperties`）：**`srm.over-receive-ratio`**（超收比例）、**`srm.export-po-type-code`**、**`srm.export-gr-type-code`**（导出单据类型编码，与 U9 映射对齐）。

### REST 摘要

| 方法 | 路径 |
|------|------|
| GET/POST | `/api/v1/ledgers`、`/api/v1/ledgers/{id}/org-units` |
| GET/POST | `/api/v1/org-units/{id}/warehouses` |
| GET/POST/PUT | `/api/v1/master/suppliers`、`/api/v1/master/materials` |
| GET/POST | `/api/v1/purchase-orders` 及 `/{id}/approve`、`/release`、`/cancel`、`/close` |
| GET | `/api/v1/purchase-orders/{poId}/asn-notices`、`.../asn-notices/{asnId}` |
| GET/POST | `/api/v1/goods-receipts`、`/goods-receipts/{id}` |
| POST | `/api/v1/exports/purchase-orders`、`/exports/goods-receipts`（body：`number[]`） |
| GET | `/api/v1/reports/purchase-execution?procurementOrgId=` |
| GET | `/api/v1/portal/purchase-orders`、`/purchase-orders/{id}` |
| POST | `/api/v1/portal/purchase-order-lines/{lineId}/confirm` |
| GET/POST | `/api/v1/portal/asn-notices`、`/asn-notices/{id}` |

**dev 演示账号**：`admin` / `admin123`；门户：`portal` / `portal123`（与供应商 S001 绑定，具体 `supplier_id` 见启动日志）。

业务方案见父工作区 [docs/SRM建设方案.md](../docs/SRM建设方案.md)。
