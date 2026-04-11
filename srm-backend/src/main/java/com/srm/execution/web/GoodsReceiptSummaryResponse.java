package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceipt;

import java.math.BigDecimal;

public record GoodsReceiptSummaryResponse(
        Long id,
        String grNo,
        Long purchaseOrderId,
        String poNo,
        Long warehouseId,
        String warehouseCode,
        String receiptDate,
        String exportStatus,
        /** 关联采购订单尚未收清数量（各订单行 max(0, 订购-累计实收) 之和） */
        String pendingReceiptQty,
        /** 是否存在至少一行关联发货通知（ASN） */
        boolean hasAsnShipment
) {
    public static GoodsReceiptSummaryResponse from(
            GoodsReceipt g, BigDecimal pendingReceiptQtyOnPo, boolean hasAsnShipment) {
        BigDecimal p = pendingReceiptQtyOnPo != null ? pendingReceiptQtyOnPo : BigDecimal.ZERO;
        return new GoodsReceiptSummaryResponse(
                g.getId(),
                g.getGrNo(),
                g.getPurchaseOrder().getId(),
                g.getPurchaseOrder().getPoNo(),
                g.getWarehouse().getId(),
                g.getWarehouse().getCode(),
                g.getReceiptDate().toString(),
                g.getExportStatus().name(),
                p.stripTrailingZeros().toPlainString(),
                hasAsnShipment
        );
    }
}
