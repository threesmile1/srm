package com.srm.execution.web;

import com.srm.execution.domain.AsnNotice;

import java.util.List;

public record AsnNoticeResponse(
        Long id,
        String asnNo,
        Long purchaseOrderId,
        String poNo,
        String status,
        String shipDate,
        String etaDate,
        String carrier,
        String trackingNo,
        String remark,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String logisticsAttachmentOriginalName,
        String logisticsAttachmentContentType,
        Long logisticsAttachmentFileSize,
        List<AsnLineResponse> lines
) {
    public static AsnNoticeResponse from(AsnNotice n) {
        return new AsnNoticeResponse(
                n.getId(),
                n.getAsnNo(),
                n.getPurchaseOrder().getId(),
                n.getPurchaseOrder().getPoNo(),
                n.getStatus().name(),
                n.getShipDate().toString(),
                n.getEtaDate() != null ? n.getEtaDate().toString() : null,
                n.getCarrier(),
                n.getTrackingNo(),
                n.getRemark(),
                n.getReceiverName(),
                n.getReceiverPhone(),
                n.getReceiverAddress(),
                n.getLogisticsAttachmentOriginalName(),
                n.getLogisticsAttachmentContentType(),
                n.getLogisticsAttachmentFileSize(),
                n.getLines().stream().map(AsnLineResponse::from).toList()
        );
    }
}
