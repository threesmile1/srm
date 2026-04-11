package com.srm.execution.web;

import com.srm.execution.service.GoodsReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "GoodsReceipt", description = "A6 收货单")
@RestController
@RequestMapping("/api/v1/goods-receipts")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @GetMapping
    public List<GoodsReceiptSummaryResponse> list(@RequestParam Long procurementOrgId) {
        return goodsReceiptService.listSummaryByOrg(procurementOrgId);
    }

    @GetMapping("/{id}")
    public GoodsReceiptDetailResponse get(@PathVariable Long id) {
        return GoodsReceiptDetailResponse.from(goodsReceiptService.requireDetail(id));
    }

    /**
     * 运维/数据修复：按采购订单将历史收货行的 {@code asn_line_id} 补全为「该订单行最新发货通知」对应的 ASN 行（与新建收货逻辑一致）。
     */
    @PostMapping("/backfill-asn")
    public GoodsReceiptService.GrAsnBackfillResult backfillAsn(@Valid @RequestBody GrAsnBackfillRequest req) {
        return goodsReceiptService.backfillAsnLineIdsForPurchaseOrder(
                req.purchaseOrderId(),
                Boolean.TRUE.equals(req.overwriteExisting()));
    }

    @PostMapping
    public GoodsReceiptDetailResponse create(@Valid @RequestBody GrCreateRequest req) {
        List<GoodsReceiptService.GrLineInput> lines = req.lines().stream()
                .map(l -> new GoodsReceiptService.GrLineInput(l.purchaseOrderLineId(), l.receivedQty(), l.asnLineId()))
                .toList();
        var gr = goodsReceiptService.create(
                req.procurementOrgId(),
                req.purchaseOrderId(),
                req.warehouseId(),
                req.receiptDate(),
                req.remark(),
                lines
        );
        return GoodsReceiptDetailResponse.from(goodsReceiptService.requireDetail(gr.getId()));
    }

    public record GrAsnBackfillRequest(
            @NotNull @Positive Long purchaseOrderId,
            /** 为 true 时对该 PO 下全部收货行重算 ASN；默认仅补 {@code asn_line_id} 为空的行 */
            Boolean overwriteExisting
    ) {}

    public record GrCreateRequest(
            @NotNull Long procurementOrgId,
            @NotNull Long purchaseOrderId,
            @NotNull Long warehouseId,
            @NotNull LocalDate receiptDate,
            String remark,
            @NotEmpty List<GrLineReq> lines
    ) {
        public record GrLineReq(
                @NotNull Long purchaseOrderLineId,
                @NotNull BigDecimal receivedQty,
                Long asnLineId
        ) {}
    }
}
