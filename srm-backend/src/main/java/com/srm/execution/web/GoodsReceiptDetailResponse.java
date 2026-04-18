package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceipt;

import java.util.List;

public record GoodsReceiptDetailResponse(
        Long id,
        String grNo,
        /** 本收货单 U9 单据编号 */
        String u9DocNo,
        Long purchaseOrderId,
        String poNo,
        /** 关联采购订单的 U9 单号 */
        String poU9DocNo,
        Long warehouseId,
        String warehouseCode,
        String receiptDate,
        String remark,
        String exportStatus,
        /** PENDING_APPROVAL / APPROVED / REJECTED */
        String status,
        List<GoodsReceiptLineResponse> lines
) {
    public static GoodsReceiptDetailResponse from(GoodsReceipt g) {
        String poU9 = g.getPurchaseOrder().getU9DocNo();
        String grU9 = g.getU9DocNo();
        return new GoodsReceiptDetailResponse(
                g.getId(),
                g.getGrNo(),
                grU9 != null && !grU9.isBlank() ? grU9 : "",
                g.getPurchaseOrder().getId(),
                g.getPurchaseOrder().getPoNo(),
                poU9 != null && !poU9.isBlank() ? poU9 : "",
                g.getWarehouse().getId(),
                g.getWarehouse().getCode(),
                g.getReceiptDate().toString(),
                g.getRemark(),
                g.getExportStatus().name(),
                g.getStatus().name(),
                g.getLines().stream().map(GoodsReceiptLineResponse::from).toList()
        );
    }
}
