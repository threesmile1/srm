package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceiptLine;

import java.math.BigDecimal;

public record GoodsReceiptLineResponse(
        Long id,
        int lineNo,
        Long purchaseOrderLineId,
        int poLineNo,
        Long asnLineId,
        String asnNo,
        String materialCode,
        BigDecimal receivedQty,
        String uom
) {
    public static GoodsReceiptLineResponse from(GoodsReceiptLine line) {
        var al = line.getAsnLine();
        var pol = line.getPurchaseOrderLine();
        return new GoodsReceiptLineResponse(
                line.getId(),
                line.getLineNo(),
                pol.getId(),
                pol.getLineNo(),
                al != null ? al.getId() : null,
                al != null ? al.getAsnNotice().getAsnNo() : null,
                pol.getMaterial().getCode(),
                line.getReceivedQty(),
                pol.getUom()
        );
    }
}
