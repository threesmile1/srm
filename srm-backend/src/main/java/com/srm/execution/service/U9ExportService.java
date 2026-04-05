package com.srm.execution.service;

import com.srm.config.SrmProperties;
import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.po.domain.ExportStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * U9 导入用 Excel：单 Sheet。列名与《SRM建设方案》附录 A 草稿对齐，冻结模板后可替换表头常量。
 */
@Service
@RequiredArgsConstructor
public class U9ExportService {

    private static final String[] PO_HEADERS = {
            "账套编码", "组织编码", "来源系统", "外部单号", "单据类型", "采购订单号", "单据日期",
            "供应商编码", "币种", "行号", "物料编码", "物料名称", "数量", "单位", "要求到货日",
            "未税单价", "收货仓库"
    };

    private static final String[] GR_HEADERS = {
            "账套编码", "组织编码", "来源系统", "单据类型", "收货单号", "单据日期", "供应商编码",
            "来源采购订单号", "来源订单行号", "仓库编码", "收货数量", "单位", "ASN单号", "备注"
    };

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SrmProperties srmProperties;

    @Transactional
    public byte[] exportPurchaseOrders(List<Long> purchaseOrderIds) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Sheet1");
            Row h = sh.createRow(0);
            for (int i = 0; i < PO_HEADERS.length; i++) {
                h.createCell(i).setCellValue(PO_HEADERS[i]);
            }
            int r = 1;
            for (Long id : purchaseOrderIds) {
                PurchaseOrder po = purchaseOrderRepository.findWithDetailsById(id)
                        .orElseThrow(() -> new NotFoundException("PO 不存在: " + id));
                String ledger = nz(po.getLedger().getU9LedgerCode(), po.getLedger().getCode());
                String org = nz(po.getProcurementOrg().getU9OrgCode(), po.getProcurementOrg().getCode());
                LocalDate poDate = po.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                for (PurchaseOrderLine line : po.getLines()) {
                    Row row = sh.createRow(r++);
                    int c = 0;
                    row.createCell(c++).setCellValue(ledger);
                    row.createCell(c++).setCellValue(org);
                    row.createCell(c++).setCellValue("SRM");
                    row.createCell(c++).setCellValue(po.getPoNo());
                    row.createCell(c++).setCellValue(srmProperties.getExportPoTypeCode());
                    row.createCell(c++).setCellValue(po.getPoNo());
                    row.createCell(c++).setCellValue(poDate.toString());
                    row.createCell(c++).setCellValue(po.getSupplier().getCode());
                    row.createCell(c++).setCellValue(po.getCurrency());
                    row.createCell(c++).setCellValue(line.getLineNo());
                    row.createCell(c++).setCellValue(line.getMaterial().getCode());
                    row.createCell(c++).setCellValue(line.getMaterial().getName());
                    row.createCell(c++).setCellValue(line.getQty().doubleValue());
                    row.createCell(c++).setCellValue(line.getUom());
                    row.createCell(c++).setCellValue(line.getRequestedDate() != null ? line.getRequestedDate().toString() : "");
                    row.createCell(c++).setCellValue(line.getUnitPrice().doubleValue());
                    row.createCell(c).setCellValue(nz(line.getWarehouse().getU9WhCode(), line.getWarehouse().getCode()));
                }
                po.setExportStatus(ExportStatus.EXPORTED);
                purchaseOrderRepository.save(po);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("导出失败", e);
        }
    }

    @Transactional
    public byte[] exportGoodsReceipts(List<Long> goodsReceiptIds) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Sheet1");
            Row h = sh.createRow(0);
            for (int i = 0; i < GR_HEADERS.length; i++) {
                h.createCell(i).setCellValue(GR_HEADERS[i]);
            }
            int r = 1;
            for (Long id : goodsReceiptIds) {
                GoodsReceipt gr = goodsReceiptRepository.findWithDetailsById(id)
                        .orElseThrow(() -> new NotFoundException("收货单不存在: " + id));
                String ledger = nz(gr.getLedger().getU9LedgerCode(), gr.getLedger().getCode());
                String org = nz(gr.getProcurementOrg().getU9OrgCode(), gr.getProcurementOrg().getCode());
                PurchaseOrder po = gr.getPurchaseOrder();
                for (var line : gr.getLines()) {
                    Row row = sh.createRow(r++);
                    int c = 0;
                    row.createCell(c++).setCellValue(ledger);
                    row.createCell(c++).setCellValue(org);
                    row.createCell(c++).setCellValue("SRM");
                    row.createCell(c++).setCellValue(srmProperties.getExportGrTypeCode());
                    row.createCell(c++).setCellValue(gr.getGrNo());
                    row.createCell(c++).setCellValue(gr.getReceiptDate().toString());
                    row.createCell(c++).setCellValue(gr.getSupplier().getCode());
                    row.createCell(c++).setCellValue(po.getPoNo());
                    row.createCell(c++).setCellValue(line.getPurchaseOrderLine().getLineNo());
                    row.createCell(c++).setCellValue(nz(gr.getWarehouse().getU9WhCode(), gr.getWarehouse().getCode()));
                    row.createCell(c++).setCellValue(line.getReceivedQty().doubleValue());
                    row.createCell(c++).setCellValue(line.getPurchaseOrderLine().getUom());
                    String asnRef = line.getAsnLine() != null ? line.getAsnLine().getAsnNotice().getAsnNo() : "";
                    row.createCell(c++).setCellValue(asnRef);
                    row.createCell(c).setCellValue(gr.getRemark() != null ? gr.getRemark() : "");
                }
                gr.setExportStatus(ExportStatus.EXPORTED);
                goodsReceiptRepository.save(gr);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("导出失败", e);
        }
    }

    private static byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return bos.toByteArray();
    }

    private static String nz(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
