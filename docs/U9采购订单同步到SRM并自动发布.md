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

## 8. U9 V6.6（BDSU9）：采购订单同步到 SRM 的字段抽取 SQL（已审核、未关闭）

U9 常见数据库为 **SQL Server**。本节 SQL **以你们 Navicat 导出的表结构为准**：

- 头表：`dbo.PM_PurchaseOrder`
- 行表：`dbo.PM_POLine`
- 计划行（用于“要求交货日期”）：`dbo.PM_POShipLine`

### 8.1 单据状态枚举（用于理解 `Status=2`）

U9 采购订单/采购订单行常见枚举：`UFIDA.U9.PM.PO.PODOCStatusEnum`

- `0`：开立
- `1`：审核中
- `2`：已审核
- `3`：自然关闭
- `4`：短缺关闭
- `5`：超额关闭

### 8.2 同步字段抽取 SQL（定稿示例）

> 过滤口径：
>
> - **已审核**：`po.Status = 2` 且 `pl.Status = 2`
> - **未作废**：`Cancel_Canceled = 0`（头/行）
> - **未关闭**：`IsBizClosed = 0` 且 `IsFIClose = 0`（头/行）
> - **核算组织**：示例过滤 `po.AccountOrg = 1001711275375071`（宁波公司）；按需修改/删除
>
> SRM 侧采购组织映射（建议）：
>
> - 在 SRM `org_unit` 增加一条采购组织（示例：`code = NB`，`name = 宁波公司`），并把 **`u9_org_code` 写成 U9 的 `AccountOrg` 数值字符串**：`1001711275375071`。
> - 同步程序用 `po.AccountOrg` → `org_unit.u9_org_code` 解析 `procurement_org_id`（找不到则进入待处理/告警）。
>
> 扩展字段说明：
>
> - 你们现场把业务信息落在头表 **`DescFlexField_PrivateDescSeg*`**（私有扩展段）。虽然 UI 文案可能写“全局段”，但 **数据库列名以 `PrivateDescSeg` 为准**。
>
> “要求交货日期”：
>
> - `PM_POLine` 上通常不直接存 `DeliveryDate`，而是在 **`PM_POShipLine`**（计划行）中。
> - 你们确认规则：**取最新计划行** → 本示例用 `ORDER BY x.ID DESC`（如需更严谨，可改为 `ORDER BY x.ModifiedOn DESC, x.ID DESC`）。
>
> **定稿口径**：下面代码块与现场使用的 SQL 一致（含 `im.code as 物料编码`、`CBO_Supplier` / `CBO_Supplier_Trl` 取供应商名称与编码），本文档以此为准存档。

```sql
DECLARE @FromDate DATETIME = DATEADD(DAY, -90, GETDATE());

SELECT
  po.ID                                             AS [PO_ID],
  pl.ID                                             AS [POLine_ID],
  pl.DocLineNo                                      AS [行号],

  po.BusinessDate                                   AS [业务日期],

  /* 头表：私有扩展段（你们现场映射；列名以数据库为准） */
  po.DescFlexField_PrivateDescSeg5                  AS [来源正式订单号_全局段5],
  po.DescFlexField_PrivateDescSeg8                  AS [二级门店_全局段8],
  po.DescFlexField_PrivateDescSeg3                  AS [收货人名称_全局段3],
  po.DescFlexField_PrivateDescSeg11                 AS [终端电话_全局段11],
  NULLIF(LTRIM(RTRIM(po.DescFlexField_PrivateDescSeg10)), '') AS [安装地址],

  pl.ItemInfo_ItemName                              AS [料品名称],
  im.SPECS                                          AS [料品规格],
  im.code as 物料编码,

  st.Name                                           AS [供应商名称],
  s.Code                                            AS [供应商编码],

  po.DocNo                                          AS [单据编号],
  po.DocumentType                                   AS [单据类型_ID],

  uom.Code                                          AS [销售单位],
  pl.PurQtyCU                                       AS [销售数量],
  pl.FinallyPriceTC                                 AS [最终价],
  pl.TotalMnyTC                                     AS [价税合计],

  /* 最新计划行交期：按计划行 ID 倒序取第一条 */
  psl.DeliveryDate                                  AS [要求交货日期],

  po.AccountOrg                                     AS [核算组织],
  CASE po.Status
    WHEN 0 THEN N'开立'
    WHEN 1 THEN N'审核中'
    WHEN 2 THEN N'已审核'
    WHEN 3 THEN N'自然关闭'
    WHEN 4 THEN N'短缺关闭'
    WHEN 5 THEN N'超额关闭'
    ELSE CAST(po.Status AS nvarchar(20))
  END                                               AS [单据状态名称]

FROM dbo.PM_PurchaseOrder po
JOIN dbo.PM_POLine pl
  ON pl.PurchaseOrder = po.ID
OUTER APPLY (
  SELECT TOP 1 x.DeliveryDate
  FROM dbo.PM_POShipLine x
  WHERE x.POLine = pl.ID
  ORDER BY x.ID DESC
) psl
LEFT JOIN dbo.CBO_ItemMaster im
  ON im.ID = pl.ItemInfo_ItemID
LEFT JOIN dbo.CBO_Supplier s
  ON s.ID = po.Supplier_Supplier
LEFT JOIN dbo.CBO_Supplier_Trl st
  ON st.ID = s.ID
 AND st.SysMlFlag = N'zh-CN'
LEFT JOIN dbo.Base_UOM uom
  ON uom.ID = pl.TradeUOM
WHERE 1 = 1
  AND ISNULL(po.Cancel_Canceled, 0) = 0
  AND po.Status = 2
  AND ISNULL(po.IsBizClosed, 0) = 0
  AND ISNULL(po.IsFIClose, 0) = 0

  AND ISNULL(pl.Cancel_Canceled, 0) = 0
  AND pl.Status = 2
  AND ISNULL(pl.IsBizClosed, 0) = 0
  AND ISNULL(pl.IsFIClose, 0) = 0

  AND po.CreatedOn >= @FromDate
  AND po.AccountOrg = 1001711275375071 -- 核算组织：宁波公司（按需修改/删除）
ORDER BY po.DocNo, pl.DocLineNo;
```

### 8.3 可选：先探测字段取值（排障用）

```sql
SELECT TOP 50
  po.DocNo,
  po.Status,
  po.IsBizClosed,
  po.IsFIClose,
  po.Cancel_Canceled,
  po.CreatedOn
FROM dbo.PM_PurchaseOrder po
ORDER BY po.CreatedOn DESC;
```

---

## 9. SRM 已实现能力（帆软 caigou_cp）

- **管理端**：采购订单列表 →「从 U9 同步采购订单（帆软）」；或调用 **`POST /api/v1/purchase-orders/sync-from-u9`**（需登录，读超时建议 10 分钟级，与物料同步一致）。
- **帆软请求体**：与物料共用 `srm.u9.decision-api-url`、`datasource-name`、`page_number` / `sync-page-size` 分页逻辑；报表路径默认 **`srm.u9.purchase-order-report-path: API/caigou_cp.cpt`**；**`parameters` 默认一条空对象 `[{}]`**（与现场一致）。若模板需要具名参数，可在配置中设置 `purchase-order-fine-report-parameters`（格式同 `fine-report-parameters`）。
- **落库规则**：按 **`purchase_order.u9_doc_no` = U9 `单据编号`** 且 **`procurement_org_id`**（由行上 **`核算组织` → `org_unit.u9_org_code`** 解析）做幂等；新建单为 **已审核并自动发布**（`RELEASED` + 供应商通知）。重复同步时，若订单**已有收货**则拒绝覆盖；否则整单替换行。
- **仓库**：当前取该采购组织下 **按编码排序的第一个仓库** 作为行仓库（与「每行非空 warehouse」约束兼容）。若报表后续增加仓库列，可再扩展映射。

---

## 10. 参考

- 采购相关表名线索（仅作为“表名参考”，字段以你们数据库为准）：`PM_PurchaseOrder`、`PM_POLine`、`PM_POShipLine`、`PM_RcvLine`、`PM_Receivement`  
  （示例来源：`https://www.cnblogs.com/friend/p/18349234`）

