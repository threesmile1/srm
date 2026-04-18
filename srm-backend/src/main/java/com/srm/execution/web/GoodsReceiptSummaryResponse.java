package com.srm.execution.web;

import com.srm.execution.domain.GoodsReceipt;

import java.math.BigDecimal;

public record GoodsReceiptSummaryResponse(
        Long id,
        String grNo,
        /** 本收货单在 U9 的单据编号（帆软同步写入）；无则空串 */
        String u9DocNo,
        Long purchaseOrderId,
        String poNo,
        /** 关联采购订单的 U9 单号（PM_PurchaseOrder.DocNo）；无则空串 */
        String poU9DocNo,
        Long warehouseId,
        String warehouseCode,
        String receiptDate,
        String exportStatus,
        String status,
        /** 关联采购订单尚未收清数量（各订单行 max(0, 订购-累计实收) 之和） */
        String pendingReceiptQty,
        /** 是否存在至少一行关联发货通知（ASN） */
        boolean hasAsnShipment,
        /** 关联采购订单是否存在已提交的发货通知（收货行可能尚未关联 ASN） */
        boolean purchaseOrderHasSubmittedAsn,
        /** 本单关联的发货通知单号，多份时英文逗号分隔；无 ASN 行为空串 */
        String asnSummary
) {
    public static GoodsReceiptSummaryResponse from(
            GoodsReceipt g,
            BigDecimal pendingReceiptQtyOnPo,
            boolean hasAsnShipment,
            boolean purchaseOrderHasSubmittedAsn,
            String asnSummary) {
        BigDecimal p = pendingReceiptQtyOnPo != null ? pendingReceiptQtyOnPo : BigDecimal.ZERO;
        String poU9 = g.getPurchaseOrder().getU9DocNo();
        String grU9 = g.getU9DocNo();
        return new GoodsReceiptSummaryResponse(
                g.getId(),
                g.getGrNo(),
                grU9 != null && !grU9.isBlank() ? grU9 : "",
                g.getPurchaseOrder().getId(),
                g.getPurchaseOrder().getPoNo(),
                poU9 != null && !poU9.isBlank() ? poU9 : "",
                g.getWarehouse().getId(),
                g.getWarehouse().getCode(),
                g.getReceiptDate().toString(),
                g.getExportStatus().name(),
                g.getStatus().name(),
                p.stripTrailingZeros().toPlainString(),
                hasAsnShipment,
                purchaseOrderHasSubmittedAsn,
                asnSummary != null ? asnSummary : ""
        );
    }
}
