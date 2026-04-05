package com.srm.execution.web;

import com.srm.execution.service.U9ExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "U9Export", description = "A7 U9 Excel 导出")
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class U9ExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final U9ExportService u9ExportService;

    @PostMapping("/purchase-orders")
    public ResponseEntity<byte[]> exportPurchaseOrders(@RequestBody List<Long> purchaseOrderIds) {
        byte[] data = u9ExportService.exportPurchaseOrders(purchaseOrderIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"srm-purchase-orders.xlsx\"")
                .contentType(XLSX)
                .body(data);
    }

    @PostMapping("/goods-receipts")
    public ResponseEntity<byte[]> exportGoodsReceipts(@RequestBody List<Long> goodsReceiptIds) {
        byte[] data = u9ExportService.exportGoodsReceipts(goodsReceiptIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"srm-goods-receipts.xlsx\"")
                .contentType(XLSX)
                .body(data);
    }
}
