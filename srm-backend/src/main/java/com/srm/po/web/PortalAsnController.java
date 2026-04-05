package com.srm.po.web;

import com.srm.execution.service.AsnService;
import com.srm.execution.web.AsnNoticeResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

@Tag(name = "PortalAsn", description = "A5 门户发货通知")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalAsnController {

    private final AsnService asnService;

    @GetMapping("/asn-notices")
    public List<AsnNoticeResponse> list(
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalPurchaseOrderController.resolveSupplierId(headerSupplierId, querySupplierId);
        return asnService.listForSupplier(sid).stream()
                .map(AsnNoticeResponse::from)
                .toList();
    }

    @GetMapping("/asn-notices/{id}")
    public AsnNoticeResponse get(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalPurchaseOrderController.resolveSupplierId(headerSupplierId, querySupplierId);
        return AsnNoticeResponse.from(asnService.requireWithLinesForSupplier(sid, id));
    }

    @PostMapping("/asn-notices")
    public AsnNoticeResponse create(
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId,
            @Valid @RequestBody PortalAsnCreateRequest body
    ) {
        long sid = PortalPurchaseOrderController.resolveSupplierId(headerSupplierId, querySupplierId);
        List<AsnService.AsnLineInput> lines = body.lines().stream()
                .map(l -> new AsnService.AsnLineInput(l.purchaseOrderLineId(), l.shipQty()))
                .toList();
        var n = asnService.createFromSupplier(
                sid,
                body.purchaseOrderId(),
                body.shipDate(),
                body.etaDate(),
                body.carrier(),
                body.trackingNo(),
                body.remark(),
                lines
        );
        return AsnNoticeResponse.from(asnService.requireWithLines(n.getId()));
    }

    public record PortalAsnCreateRequest(
            @NotNull Long purchaseOrderId,
            @NotNull LocalDate shipDate,
            LocalDate etaDate,
            String carrier,
            String trackingNo,
            String remark,
            @NotEmpty List<PortalAsnLineReq> lines
    ) {
        public record PortalAsnLineReq(
                @NotNull Long purchaseOrderLineId,
                @NotNull BigDecimal shipQty
        ) {}
    }
}
