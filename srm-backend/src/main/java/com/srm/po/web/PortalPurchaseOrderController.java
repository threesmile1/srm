package com.srm.po.web;

import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.service.PurchaseOrderService;
import com.srm.web.error.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A4 供应商门户 API。一期联调：通过请求头 {@code X-Dev-Supplier-Id} 或查询参数 {@code supplierId} 标识供应商。
 */
@Tag(name = "PortalPO", description = "A4 门户采购协同")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalPurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderController.PoSummaryResponse> listReleased(
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = resolveSupplierId(headerSupplierId, querySupplierId);
        return purchaseOrderService.listReleasedForSupplier(sid).stream()
                .map(PurchaseOrderController.PoSummaryResponse::from)
                .toList();
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderController.PoDetailResponse getDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = resolveSupplierId(headerSupplierId, querySupplierId);
        PurchaseOrder po = purchaseOrderService.requireDetail(id);
        if (!po.getSupplier().getId().equals(sid)) {
            throw new BadRequestException("无权查看该订单");
        }
        return PurchaseOrderController.PoDetailResponse.from(po);
    }

    @PostMapping("/purchase-order-lines/{lineId}/confirm")
    public PurchaseOrderController.PoLineResponse confirmLine(
            @PathVariable Long lineId,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId,
            @Valid @RequestBody LineConfirmRequest body
    ) {
        long sid = resolveSupplierId(headerSupplierId, querySupplierId);
        PurchaseOrderLine line = purchaseOrderService.confirmLine(
                sid, lineId, body.confirmedQty(), body.promisedDate(), body.supplierRemark());
        return PurchaseOrderController.PoLineResponse.from(line);
    }

    public static long resolveSupplierId(Long headerSupplierId, Long querySupplierId) {
        if (headerSupplierId != null) {
            return headerSupplierId;
        }
        if (querySupplierId != null) {
            return querySupplierId;
        }
        throw new BadRequestException("请设置请求头 X-Dev-Supplier-Id 或查询参数 supplierId（一期联调）");
    }

    public record LineConfirmRequest(
            @NotNull BigDecimal confirmedQty,
            LocalDate promisedDate,
            String supplierRemark
    ) {}
}
