# srm-portal-web

SRM **供应商门户**：**Vue 3** + **TypeScript** + **Vite** + **Element Plus** + **Vue Router**。

## 开发

```powershell
cd srm-portal-web
npm install
npm run dev
```

默认 **http://localhost:5174** ，API 基地址见 `.env.development`（`VITE_API_BASE`）。联调供应商身份：`VITE_DEV_SUPPLIER_ID`（默认 `1`）。

## 已实现页面（阶段 A MVP）

- 已发布采购订单列表与详情、**订单行确认**  
- **发货通知 (ASN)**：列表、新建（可从订单详情带 `poId` 跳转）

## 构建

```powershell
npm run build
```

业务方案见 [docs/SRM建设方案.md](../docs/SRM建设方案.md)（父工作区文档仓）。
