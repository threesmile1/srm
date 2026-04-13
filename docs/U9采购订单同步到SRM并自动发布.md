# U9 采购订单（已审核未关闭）同步到 SRM 并自动发布（建议）

## 1. 业务目标

业务场景：**U9 自己生成的采购订单**（在 U9 状态为**已审核、未关闭**）同步到 SRM 系统中来，并在 SRM 中**直接发布给供应商**（进入供应商协同/门户确认/ASN/收货等流程）。

核心思路：U9 已完成“审核”这一步，SRM 作为协同平台，不再重复审批链路。

---

## 2. 推荐状态映射（最小可用）

建议在同步写入 SRM 时直接落状态（不要先落草稿再走审批）：

- **U9：已审核 & 未关闭** → **SRM：`RELEASED`**（已发布）
- **U9：已关闭/终止/作废** → **SRM：`CLOSED`/`CANCELLED`**（按你们口径选一种并固定）

> 注：如果 U9 存在“反审核/弃审”，建议 SRM 不做“已发布回退”，而是走“变更/撤销发布”的受控流程，避免供应商侧已确认/已 ASN/已收货导致数据冲突。

---

## 3. 幂等与唯一键（避免重复单/重复行）

强烈建议在 SRM 侧引入外部来源字段（或映射表）实现幂等：

- **PO 头唯一键**：`source_system = 'U9'` + `u9_po_no`
- **PO 行唯一键**：`source_system = 'U9'` + `u9_po_no` + `u9_line_no`

同步逻辑采用 **upsert**：

- 同一张 U9 PO 重复同步，不会在 SRM 生成重复 PO。
- 行增删改时可按 `u9_line_no` 做精确比对。

---

## 4. 仓库/收货地点（必须能落地）

你们 SRM 的 PO 行目前 `warehouse_id` 为非空（数据库/实体约束），因此同步时必须保证“每行能确定仓库”：

- **优先**：U9 同步数据直接携带仓库编码/仓库 ID（推荐）
- **其次**：按规则推导（例如：采购组织/工厂 + 物料默认仓 → SRM `warehouse` 主档）
- **否则**：进入“待处理队列”（不发布），由主数据维护后重试同步

---

## 5. 变更同步策略（避免覆盖供应商协同数据）

U9 的采购订单可能后续被修改（交期/数量/行增删/关闭等），建议分层处理：

- **第一阶段（快）**：只同步“已审核未关闭”的新增 PO；变更先只记录日志/告警，不覆盖 SRM 已发布数据。
- **第二阶段（稳）**：对可覆盖字段做增量同步（例如交期），但对以下情况要保护：
  - 供应商已确认（SRM 有确认记录）
  - 已产生 ASN
  - 已收货
  - 已对账/开票

对冲突场景建议生成“变更通知/待采购确认”的工作项，而不是强行覆盖。

---

## 6. 发布与通知（供应商侧体验）

同步落 `RELEASED` 后应触发：

- SRM 供应商通知（站内信/消息）
- 门户侧“待确认订单”可见

建议把 U9 的“审核人/审核时间/备注”同步到 SRM（用于审计与沟通）。

---

## 7. 失败重试与审计

- 同步/发布必须可重试（幂等键保证安全重跑）
- 每次同步写审计日志：来源、入参摘要、写入结果、异常原因

---

## 8. U9 V6.6：查询“已审核、未关闭”的采购订单 SQL（模板）

U9 常见数据库为 **SQL Server**。以下为“模板 SQL”，你可以先用它做验证，然后按你们现场字段口径微调。

### 8.1 先探测字段取值（建议先跑一次）

```sql
-- 观察状态字段取值分布（先确认已审核/关闭对应的值）
SELECT TOP 50
  po.DocNo,
  po.Status,
  po.IsBizClosed,
  po.IsFIClose,
  po.Cancel_Canceled,
  po.CreatedOn
FROM PM_PurchaseOrder po
ORDER BY po.CreatedOn DESC;
```

### 8.2 查询“已审核、未关闭”的 PO（主表）

> 说明：你们现场口径 **`Status=2` 为“已审核”**；关闭相关常见有 `IsBizClosed` / `IsFIClose`。另外不同项目库里 `Supplier_Name`、`Currency_Code` 等“冗余展示列”不一定存在，推荐先只取主表通用字段，供应商名称/币种名称再按外键去 JOIN。

```sql
DECLARE @FromDate DATETIME = DATEADD(DAY, -30, GETDATE()); -- 最近 30 天，可改

SELECT
  po.ID,
  po.DocNo,
  po.BusinessDate,
  po.Status,
  po.IsBizClosed,
  po.IsFIClose,
  po.Cancel_Canceled,
  po.CreatedOn,
  po.ModifiedOn
FROM PM_PurchaseOrder po
WHERE 1=1
  AND ISNULL(po.Cancel_Canceled, 0) = 0
  AND po.Status = 2               -- 已审核（你们现场口径）
  AND ISNULL(po.IsBizClosed, 0) = 0
  AND ISNULL(po.IsFIClose, 0) = 0
  AND po.CreatedOn >= @FromDate
ORDER BY po.CreatedOn DESC;
```

### 8.3 带出订单行（主表 + 行表）

```sql
DECLARE @FromDate DATETIME = DATEADD(DAY, -30, GETDATE());

SELECT
  po.DocNo              AS PO_No,
  po.BusinessDate       AS PO_Date,
  pl.DocLineNo          AS LineNo,          -- 你们环境行号列名为 DocLineNo（不是 LineNo）
  pl.ItemInfo_ItemCode  AS ItemCode,
  pl.ItemInfo_ItemName  AS ItemName,
  pl.OrderQtyCU         AS Qty,
  pl.OrderUOM_Code      AS Uom,
  pl.FinalPrice         AS UnitPrice,
  pl.AmountTC           AS Amount,
  pl.DeliveryDate       AS DeliveryDate,
  pl.Wh                 AS WarehouseId,
  pl.Status             AS LineStatus
FROM PM_PurchaseOrder po
JOIN PM_POLine pl ON pl.PurchaseOrder = po.ID
WHERE 1=1
  AND ISNULL(po.Cancel_Canceled, 0) = 0
  AND po.Status = 2
  AND ISNULL(po.IsBizClosed, 0) = 0
  AND ISNULL(po.IsFIClose, 0) = 0
  AND po.CreatedOn >= @FromDate
ORDER BY po.DocNo, pl.DocLineNo;
```

> 注意：
>
>- 行表字段（数量/单价/交期/仓库）在不同项目/补丁/委外单据类型下可能列名不同；若上面字段不存在，请先 `SELECT TOP 1 * FROM PM_POLine` 看实际列名再替换。
>- 如果你还想取 **供应商/币种名称**，建议用 PO 主表上的外键去 JOIN 对应的 `_Trl` 表（例如供应商 `CBO_Supplier_Trl`）。由于各项目外键列名可能不同（如 `Supplier_Supplier`、`Supplier`、`SupplierID`），推荐先用 `SELECT TOP 1 * FROM PM_PurchaseOrder` 确认外键列名后再补 JOIN。

---

## 9. 参考

- 采购相关表名线索（仅作为“表名参考”，字段以你们数据库为准）：`PM_PurchaseOrder`、`PM_POLine`、`PM_POShipLine`、`PM_RcvLine`、`PM_Receivement`  
  （示例来源：`https://www.cnblogs.com/friend/p/18349234`）

