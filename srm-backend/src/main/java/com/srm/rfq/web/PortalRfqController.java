package com.srm.rfq.web;

import com.srm.rfq.domain.Rfq;
import com.srm.rfq.domain.RfqInvitation;
import com.srm.rfq.domain.RfqQuotation;
import com.srm.rfq.domain.RfqStatus;
import com.srm.rfq.repo.RfqRepository;
import com.srm.rfq.service.RfqService;
import com.srm.rfq.service.RfqService.QuotLineInput;
import com.srm.foundation.web.PortalSupplierSession;
import com.srm.web.error.ForbiddenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
import java.util.List;

@Tag(name = "PortalRFQ", description = "门户询价协同")
@RestController
@RequestMapping("/api/v1/portal/rfq")
@RequiredArgsConstructor
public class PortalRfqController {

    private final RfqService rfqService;
    private final RfqRepository rfqRepository;

    @Transactional(readOnly = true)
    @GetMapping
    public List<RfqController.RfqSummaryResponse> list(
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        List<RfqStatus> portalStatuses = List.of(
                RfqStatus.PUBLISHED, RfqStatus.EVALUATING, RfqStatus.AWARDED);
        return rfqRepository.findInvitedForSupplierWithStatuses(sid, portalStatuses).stream()
                .map(RfqController.RfqSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public RfqController.RfqDetailResponse get(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        Rfq rfq = rfqService.requireDetail(id);
        boolean invited = rfq.getInvitations().stream()
                .anyMatch(inv -> inv.getSupplier().getId().equals(sid));
        if (!invited) {
            throw new ForbiddenException("无权查看此询价单");
        }
        return RfqController.RfqDetailResponse.from(rfq);
    }

    @PostMapping("/{rfqId}/quotation")
    public RfqController.QuotationDetailResponse submitQuotation(
            @PathVariable Long rfqId,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId,
            @Valid @RequestBody QuotationSubmitRequest req
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        Rfq rfq = rfqService.requireDetail(rfqId);
        boolean invited = rfq.getInvitations().stream()
                .anyMatch(inv -> inv.getSupplier().getId().equals(sid));
        if (!invited) {
            throw new ForbiddenException("供应商未被邀请参与此询价");
        }

        List<QuotLineInput> lines = req.lines().stream()
                .map(l -> new QuotLineInput(l.rfqLineId(), l.unitPrice(), l.remark()))
                .toList();
        RfqQuotation quotation = rfqService.submitQuotation(
                rfqId, sid, req.currency(), req.deliveryDays(), req.validityDays(),
                req.remark(), lines);

        return RfqController.QuotationDetailResponse.from(
                rfqService.listQuotations(rfqId).stream()
                        .filter(q -> q.getId().equals(quotation.getId()))
                        .findFirst()
                        .orElse(quotation));
    }

    public record QuotationSubmitRequest(
            String currency,
            Integer deliveryDays,
            Integer validityDays,
            String remark,
            @NotEmpty List<QuotLineRequest> lines
    ) {}

    public record QuotLineRequest(
            @NotNull Long rfqLineId,
            @NotNull BigDecimal unitPrice,
            String remark
    ) {}
}
