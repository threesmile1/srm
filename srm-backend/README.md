# srm-backend

SRM **后端**：Java **17**、**Spring Boot 3.4**、**MySQL 8**、**Flyway**、**SpringDoc OpenAPI**。

## 环境要求

- **JDK 17+**、**Maven 3.9+**
- **Docker**（推荐，用于启动 MySQL）

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

## 启动 MySQL

在 `srm-backend` 目录：

```powershell
docker compose up -d
```

默认：`localhost:3306`，库名 `srm`，用户/密码 `srm`/`srm`（与 `application.yml` 一致）。

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

生产使用 **`spring.profiles.active=prod`**，关闭 dev 种子并配置数据源与安全策略。

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
