package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceipt;

import java.util.List;

public record GoodsReceiptDetailResponse(
        Long id,
        String grNo,
        Long purchaseOrderId,
        String poNo,
        Long warehouseId,
        String warehouseCode,
        String receiptDate,
        String remark,
        String exportStatus,
        List<GoodsReceiptLineResponse> lines
) {
    public static GoodsReceiptDetailResponse from(GoodsReceipt g) {
        return new GoodsReceiptDetailResponse(
                g.getId(),
                g.getGrNo(),
                g.getPurchaseOrder().getId(),
                g.getPurchaseOrder().getPoNo(),
                g.getWarehouse().getId(),
                g.getWarehouse().getCode(),
                g.getReceiptDate().toString(),
                g.getRemark(),
                g.getExportStatus().name(),
                g.getLines().stream().map(GoodsReceiptLineResponse::from).toList()
        );
    }
}
