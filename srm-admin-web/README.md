# srm-admin-web

SRM **管理端**：**Vue 3** + **TypeScript** + **Vite** + **Element Plus** + **Pinia** + **Vue Router**。

## 开发

```powershell
cd srm-admin-web
npm install
npm run dev
```

默认 **http://localhost:5173** ，通过 `VITE_API_BASE`（见 `.env.development`）指向后端 `http://localhost:8080`。

## 已实现页面（阶段 A MVP）

- 主数据：供应商、物料  
- 采购订单：列表（含 **U9 导出多选**）、新建、详情（**订单行 / ASN** 页签、跳转录入收货）  
- **收货单**：列表（明细抽屉、导出选中）、新建收货  
- **采购执行报表**（在途：订购 / 已收 / 未清）

## 构建

```powershell
npm run build
```

业务方案见上级目录 [docs/SRM建设方案.md](../docs/SRM建设方案.md)（父工作区文档仓）。
