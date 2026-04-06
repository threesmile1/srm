package com.srm.po.web;

import com.srm.foundation.web.PortalSupplierSession;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.service.PurchaseOrderService;
import com.srm.web.error.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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
 * A4 供应商门户 API。供应商身份以会话 {@code SESSION_SUPPLIER_ID} 为准；无则可传 {@code X-Dev-Supplier-Id} / {@code supplierId} 联调。
 */
@Tag(name = "PortalPO", description = "A4 门户采购协同")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalPurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @Transactional(readOnly = true)
    @GetMapping("/purchase-orders")
    public List<PurchaseOrderController.PoSummaryResponse> listReleased(
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        return purchaseOrderService.listReleasedForSupplier(sid).stream()
                .map(PurchaseOrderController.PoSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderController.PoDetailResponse getDetail(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        PurchaseOrder po = purchaseOrderService.requireDetail(id);
        if (!po.getSupplier().getId().equals(sid)) {
            throw new NotFoundException("订单不存在");
        }
        return PurchaseOrderController.PoDetailResponse.from(po);
    }

    @PostMapping("/purchase-order-lines/{lineId}/confirm")
    public PurchaseOrderController.PoLineResponse confirmLine(
            @PathVariable Long lineId,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId,
            @Valid @RequestBody LineConfirmRequest body
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        PurchaseOrderLine line = purchaseOrderService.confirmLine(
                sid, lineId, body.confirmedQty(), body.promisedDate(), body.supplierRemark());
        return PurchaseOrderController.PoLineResponse.from(line);
    }

    public record LineConfirmRequest(
            @NotNull BigDecimal confirmedQty,
            LocalDate promisedDate,
            String supplierRemark
    ) {}
}
