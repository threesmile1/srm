package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceipt;

public record GoodsReceiptSummaryResponse(
        Long id,
        String grNo,
        Long purchaseOrderId,
        String poNo,
        Long warehouseId,
        String warehouseCode,
        String receiptDate,
        String exportStatus
) {
    public static GoodsReceiptSummaryResponse from(GoodsReceipt g) {
        return new GoodsReceiptSummaryResponse(
                g.getId(),
                g.getGrNo(),
                g.getPurchaseOrder().getId(),
                g.getPurchaseOrder().getPoNo(),
                g.getWarehouse().getId(),
                g.getWarehouse().getCode(),
                g.getReceiptDate().toString(),
                g.getExportStatus().name()
        );
    }
}
