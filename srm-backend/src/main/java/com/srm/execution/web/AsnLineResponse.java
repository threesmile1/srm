package com.srm.execution.web;

import com.srm.execution.domain.AsnLine;

import java.math.BigDecimal;

public record AsnLineResponse(
        Long id,
        int lineNo,
        Long purchaseOrderLineId,
        int poLineNo,
        String materialCode,
        String materialName,
        BigDecimal shipQty
) {
    public static AsnLineResponse from(AsnLine line) {
        var pol = line.getPurchaseOrderLine();
        return new AsnLineResponse(
                line.getId(),
                line.getLineNo(),
                pol.getId(),
                pol.getLineNo(),
                pol.getMaterial().getCode(),
                pol.getMaterial().getName(),
                line.getShipQty()
        );
    }
}
