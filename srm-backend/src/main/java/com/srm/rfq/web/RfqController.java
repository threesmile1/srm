package com.srm.rfq.web;

import com.srm.rfq.domain.Rfq;
import com.srm.rfq.domain.RfqInvitation;
import com.srm.rfq.domain.RfqLine;
import com.srm.rfq.domain.RfqQuotation;
import com.srm.rfq.domain.RfqQuotationLine;
import com.srm.rfq.service.RfqService;
import com.srm.rfq.service.RfqService.CreateRfqLine;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

@Tag(name = "RFQ", description = "询价管理")
@RestController
@RequestMapping("/api/v1/rfq")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;

    @GetMapping
    public List<RfqSummaryResponse> list(@RequestParam Long procurementOrgId) {
        return rfqService.listByOrg(procurementOrgId).stream()
                .map(RfqSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RfqDetailResponse get(@PathVariable Long id) {
        return RfqDetailResponse.from(rfqService.requireDetail(id));
    }

    @PostMapping
    public RfqDetailResponse create(@Valid @RequestBody RfqCreateRequest req) {
        List<CreateRfqLine> lines = req.lines().stream()
                .map(l -> new CreateRfqLine(l.materialId(), l.qty(), l.uom(), l.specification(), l.remark()))
                .toList();
        Rfq rfq = rfqService.create(
                req.procurementOrgId(), req.title(), req.deadline(), req.remark(),
                lines, req.supplierIds());
        return RfqDetailResponse.from(rfqService.requireDetail(rfq.getId()));
    }

    @PostMapping("/{id}/publish")
    public RfqDetailResponse publish(@PathVariable Long id) {
        rfqService.publish(id);
        return RfqDetailResponse.from(rfqService.requireDetail(id));
    }

    @GetMapping("/{id}/quotations")
    public List<QuotationSummaryResponse> listQuotations(@PathVariable Long id) {
        return rfqService.listQuotations(id).stream()
                .map(QuotationSummaryResponse::from)
                .toList();
    }

    @PostMapping("/{id}/award")
    public RfqDetailResponse award(@PathVariable Long id, @RequestParam Long winningSupplierId) {
        rfqService.award(id, winningSupplierId);
        return RfqDetailResponse.from(rfqService.requireDetail(id));
    }

    // ── Request DTOs ──

    public record RfqCreateRequest(
            @NotNull Long procurementOrgId,
            @NotBlank String title,
            LocalDate deadline,
            String remark,
            @NotEmpty List<RfqLineCreateRequest> lines,
            List<Long> supplierIds
    ) {}

    public record RfqLineCreateRequest(
            @NotNull Long materialId,
            @NotNull BigDecimal qty,
            String uom,
            String specification,
            String remark
    ) {}

    // ── Response DTOs ──

    public record RfqSummaryResponse(
            Long id,
            String rfqNo,
            String title,
            String status,
            Long procurementOrgId,
            String procurementOrgCode,
            LocalDate publishDate,
            LocalDate deadline
    ) {
        static RfqSummaryResponse from(Rfq rfq) {
            return new RfqSummaryResponse(
                    rfq.getId(),
                    rfq.getRfqNo(),
                    rfq.getTitle(),
                    rfq.getStatus().name(),
                    rfq.getProcurementOrg().getId(),
                    rfq.getProcurementOrg().getCode(),
                    rfq.getPublishDate(),
                    rfq.getDeadline()
            );
        }
    }

    public record RfqDetailResponse(
            Long id,
            String rfqNo,
            String title,
            String status,
            Long procurementOrgId,
            String procurementOrgCode,
            LocalDate publishDate,
            LocalDate deadline,
            String remark,
            List<RfqLineResponse> lines,
            List<InvitationResponse> invitations
    ) {
        static RfqDetailResponse from(Rfq rfq) {
            return new RfqDetailResponse(
                    rfq.getId(),
                    rfq.getRfqNo(),
                    rfq.getTitle(),
                    rfq.getStatus().name(),
                    rfq.getProcurementOrg().getId(),
                    rfq.getProcurementOrg().getCode(),
                    rfq.getPublishDate(),
                    rfq.getDeadline(),
                    rfq.getRemark(),
                    rfq.getLines().stream().map(RfqLineResponse::from).toList(),
                    rfq.getInvitations().stream().map(InvitationResponse::from).toList()
            );
        }
    }

    public record RfqLineResponse(
            Long id,
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            BigDecimal qty,
            String uom,
            String specification,
            String remark
    ) {
        static RfqLineResponse from(RfqLine line) {
            return new RfqLineResponse(
                    line.getId(),
                    line.getLineNo(),
                    line.getMaterial().getId(),
                    line.getMaterial().getCode(),
                    line.getMaterial().getName(),
                    line.getQty(),
                    line.getUom(),
                    line.getSpecification(),
                    line.getRemark()
            );
        }
    }

    public record InvitationResponse(
            Long id,
            Long supplierId,
            String supplierCode,
            String supplierName,
            boolean responded
    ) {
        static InvitationResponse from(RfqInvitation inv) {
            return new InvitationResponse(
                    inv.getId(),
                    inv.getSupplier().getId(),
                    inv.getSupplier().getCode(),
                    inv.getSupplier().getName(),
                    inv.isResponded()
            );
        }
    }

    public record QuotationSummaryResponse(
            Long id,
            Long supplierId,
            String supplierCode,
            String supplierName,
            BigDecimal totalAmount,
            String currency,
            Integer deliveryDays,
            Integer validityDays,
            String submittedAt
    ) {
        static QuotationSummaryResponse from(RfqQuotation q) {
            return new QuotationSummaryResponse(
                    q.getId(),
                    q.getSupplier().getId(),
                    q.getSupplier().getCode(),
                    q.getSupplier().getName(),
                    q.getTotalAmount(),
                    q.getCurrency(),
                    q.getDeliveryDays(),
                    q.getValidityDays(),
                    q.getSubmittedAt() != null ? q.getSubmittedAt().toString() : null
            );
        }
    }

    public record QuotationDetailResponse(
            Long id,
            Long supplierId,
            String supplierCode,
            String supplierName,
            BigDecimal totalAmount,
            String currency,
            Integer deliveryDays,
            Integer validityDays,
            String remark,
            String submittedAt,
            List<QuotationLineResponse> lines
    ) {
        static QuotationDetailResponse from(RfqQuotation q) {
            return new QuotationDetailResponse(
                    q.getId(),
                    q.getSupplier().getId(),
                    q.getSupplier().getCode(),
                    q.getSupplier().getName(),
                    q.getTotalAmount(),
                    q.getCurrency(),
                    q.getDeliveryDays(),
                    q.getValidityDays(),
                    q.getRemark(),
                    q.getSubmittedAt() != null ? q.getSubmittedAt().toString() : null,
                    q.getQuotationLines().stream().map(QuotationLineResponse::from).toList()
            );
        }
    }

    public record QuotationLineResponse(
            Long id,
            Long rfqLineId,
            int lineNo,
            String materialCode,
            String materialName,
            BigDecimal unitPrice,
            BigDecimal amount,
            String remark
    ) {
        static QuotationLineResponse from(RfqQuotationLine ql) {
            return new QuotationLineResponse(
                    ql.getId(),
                    ql.getRfqLine().getId(),
                    ql.getRfqLine().getLineNo(),
                    ql.getRfqLine().getMaterial().getCode(),
                    ql.getRfqLine().getMaterial().getName(),
                    ql.getUnitPrice(),
                    ql.getAmount(),
                    ql.getRemark()
            );
        }
    }
}
