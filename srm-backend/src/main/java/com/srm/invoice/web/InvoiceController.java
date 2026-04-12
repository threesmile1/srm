package com.srm.invoice.web;

import com.srm.foundation.web.AuthController;
import com.srm.invoice.domain.*;
import com.srm.invoice.service.InvoiceService;
import com.srm.web.error.BadRequestException;
import com.srm.invoice.service.InvoiceService.InvoiceLineInput;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Invoice", description = "发票与对账")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    // --- Invoice ---

    @GetMapping("/invoices")
    public List<InvoiceSummary> listInvoices(@RequestParam(required = false) Long procurementOrgId,
                                              @RequestParam(required = false) Long supplierId,
                                              HttpServletRequest httpReq) {
        Long effectiveOrgId = procurementOrgId;
        if (effectiveOrgId == null && supplierId == null) {
            HttpSession session = httpReq.getSession(false);
            if (session != null) {
                Object o = session.getAttribute(AuthController.SESSION_DEFAULT_ORG_ID);
                if (o instanceof Long lid) {
                    effectiveOrgId = lid;
                }
            }
        }
        List<Invoice> list;
        if (supplierId != null) {
            list = invoiceService.listBySupplier(supplierId);
        } else if (effectiveOrgId != null) {
            list = invoiceService.listByOrg(effectiveOrgId);
        } else {
            list = List.of();
        }
        return list.stream().map(InvoiceSummary::from).toList();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDetail getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceDetailDto(id);
    }

    @PostMapping("/invoices")
    public InvoiceDetail createInvoice(@Valid @RequestBody InvoiceCreateRequest req) {
        List<InvoiceLineInput> lines = req.lines().stream()
                .map(l -> new InvoiceLineInput(l.materialCode(), l.materialName(),
                        l.qty(), l.unitPrice(), l.taxRate(),
                        l.purchaseOrderId(), l.purchaseOrderLineId(), l.goodsReceiptId()))
                .toList();
        return invoiceService.createInvoice(
                req.supplierId(), req.procurementOrgId(), req.invoiceDate(),
                req.currency(), req.taxAmount(), req.remark(),
                parseInvoiceKind(req.invoiceKind()),
                req.vatInvoiceCode(), req.vatInvoiceNumber(),
                lines);
    }

    @PostMapping("/invoices/{id}/confirm")
    public InvoiceDetail confirmInvoice(@PathVariable Long id) {
        return invoiceService.confirmInvoice(id);
    }

    @PostMapping("/invoices/{id}/reject")
    public InvoiceDetail rejectInvoice(@PathVariable Long id,
                                        @RequestBody(required = false) RejectRequest req) {
        return invoiceService.rejectInvoice(id, req != null ? req.reason() : null);
    }

    /** 采购端下载供应商上传的发票附件 */
    @GetMapping("/invoices/{invoiceId}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> downloadInvoiceAttachment(@PathVariable Long invoiceId,
                                                              @PathVariable Long attachmentId) {
        InvoiceService.InvoiceAttachmentDownload d =
                invoiceService.openInvoiceAttachmentDownload(invoiceId, attachmentId, null);
        return attachmentResponse(d);
    }

    static ResponseEntity<Resource> attachmentResponse(InvoiceService.InvoiceAttachmentDownload d) {
        HttpHeaders headers = new HttpHeaders();
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (d.contentType() != null && !d.contentType().isBlank()) {
            try {
                mt = MediaType.parseMediaType(d.contentType());
            } catch (Exception ignored) {
            }
        }
        headers.setContentType(mt);
        String fname = d.originalFileName() != null ? d.originalFileName() : "attachment";
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fname, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(d.resource());
    }

    // --- Reconciliation ---

    @GetMapping("/reconciliations")
    public List<ReconSummary> listRecon(@RequestParam(required = false) Long procurementOrgId,
                                         @RequestParam(required = false) Long supplierId) {
        List<Reconciliation> list;
        if (supplierId != null) {
            list = invoiceService.listReconBySupplier(supplierId);
        } else if (procurementOrgId != null) {
            list = invoiceService.listReconByOrg(procurementOrgId);
        } else {
            list = List.of();
        }
        return list.stream().map(ReconSummary::from).toList();
    }

    @PostMapping("/reconciliations")
    public ReconSummary createRecon(@Valid @RequestBody ReconCreateRequest req) {
        Reconciliation recon = invoiceService.createReconciliation(
                req.supplierId(), req.procurementOrgId(),
                req.periodFrom(), req.periodTo(), req.remark(), false);
        return ReconSummary.from(recon);
    }

    @PostMapping("/reconciliations/{id}/confirm")
    public ReconSummary confirmRecon(@PathVariable Long id) {
        return ReconSummary.from(invoiceService.confirmReconciliationByProcurement(id));
    }

    @PostMapping("/reconciliations/{id}/procurement-reject")
    public ReconSummary procurementRejectRecon(@PathVariable Long id,
                                                  @Valid @RequestBody ReconReasonRequest req) {
        return ReconSummary.from(invoiceService.procurementRejectReconciliation(id, req.reason()));
    }

    @PostMapping("/reconciliations/{id}/procurement-dispute")
    public ReconSummary procurementDisputeRecon(@PathVariable Long id,
                                                   @Valid @RequestBody ReconReasonRequest req) {
        return ReconSummary.from(invoiceService.procurementDisputeReconciliation(id, req.reason()));
    }

    @PostMapping("/reconciliations/{id}/reopen")
    public ReconSummary reopenRecon(@PathVariable Long id) {
        return ReconSummary.from(invoiceService.procurementReopenFromDispute(id));
    }

    // --- DTOs ---

    public record InvoiceCreateRequest(
            @NotNull Long supplierId,
            @NotNull Long procurementOrgId,
            @NotNull LocalDate invoiceDate,
            String currency,
            BigDecimal taxAmount,
            String remark,
            /** ORDINARY_VAT | SPECIAL_VAT */
            String invoiceKind,
            String vatInvoiceCode,
            String vatInvoiceNumber,
            @NotEmpty List<InvLineReq> lines
    ) {}

    public record InvLineReq(
            String materialCode, String materialName,
            @NotNull BigDecimal qty, @NotNull BigDecimal unitPrice,
            BigDecimal taxRate,
            Long purchaseOrderId, Long purchaseOrderLineId, Long goodsReceiptId
    ) {}

    public record RejectRequest(String reason) {}

    public record ReconReasonRequest(@NotBlank String reason) {}

    public record ReconCreateRequest(
            @NotNull Long supplierId,
            @NotNull Long procurementOrgId,
            @NotNull LocalDate periodFrom,
            @NotNull LocalDate periodTo,
            String remark
    ) {}

    public record InvoiceSummary(
            Long id, String invoiceNo, Long supplierId, String supplierCode, String supplierName,
            LocalDate invoiceDate, BigDecimal totalAmount, BigDecimal taxAmount,
            String currency, String status,
            String invoiceKind, String vatInvoiceCode, String vatInvoiceNumber
    ) {
        static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getInvoiceNo(),
                    i.getSupplier().getId(), i.getSupplier().getCode(), i.getSupplier().getName(),
                    i.getInvoiceDate(), i.getTotalAmount(), i.getTaxAmount(),
                    i.getCurrency(), i.getStatus().name(),
                    i.getInvoiceKind().name(),
                    i.getVatInvoiceCode(), i.getVatInvoiceNumber());
        }
    }

    public record InvoiceDetail(
            Long id, String invoiceNo, Long supplierId, String supplierCode, String supplierName,
            Long procurementOrgId, String procurementOrgCode,
            LocalDate invoiceDate, BigDecimal totalAmount, BigDecimal taxAmount,
            String currency, String status, String remark,
            String invoiceKind, String vatInvoiceCode, String vatInvoiceNumber,
            List<InvLineResponse> lines,
            List<InvoiceAttachmentItem> attachments
    ) {
        public static InvoiceDetail from(Invoice i) {
            List<InvoiceAttachmentItem> atts = i.getAttachments() == null ? List.of()
                    : i.getAttachments().stream()
                    .map(a -> new InvoiceAttachmentItem(a.getId(), a.getOriginalName(),
                            a.getContentType(), a.getFileSize()))
                    .toList();
            return new InvoiceDetail(i.getId(), i.getInvoiceNo(),
                    i.getSupplier().getId(), i.getSupplier().getCode(), i.getSupplier().getName(),
                    i.getProcurementOrg().getId(), i.getProcurementOrg().getCode(),
                    i.getInvoiceDate(), i.getTotalAmount(), i.getTaxAmount(),
                    i.getCurrency(), i.getStatus().name(), i.getRemark(),
                    i.getInvoiceKind().name(),
                    i.getVatInvoiceCode(), i.getVatInvoiceNumber(),
                    i.getLines().stream().map(InvLineResponse::from).toList(),
                    atts);
        }
    }

    public record InvoiceAttachmentItem(Long id, String originalName, String contentType, long fileSize) {}

    public record InvLineResponse(
            Long id, int lineNo, String materialCode, String materialName,
            BigDecimal qty, BigDecimal unitPrice, BigDecimal amount, BigDecimal taxRate,
            Long purchaseOrderId, String poNo, Long goodsReceiptId
    ) {
        static InvLineResponse from(InvoiceLine l) {
            return new InvLineResponse(l.getId(), l.getLineNo(),
                    l.getMaterialCode(), l.getMaterialName(),
                    l.getQty(), l.getUnitPrice(), l.getAmount(), l.getTaxRate(),
                    l.getPurchaseOrder() != null ? l.getPurchaseOrder().getId() : null,
                    l.getPurchaseOrder() != null ? l.getPurchaseOrder().getPoNo() : null,
                    l.getGoodsReceipt() != null ? l.getGoodsReceipt().getId() : null);
        }
    }

    /**
     * @param diffAmount     收货 − 已确认发票（核心对账差异，甄云类：暂估/收货应付 vs 票）
     * @param diffPoGrAmount 订单 − 收货（到货执行差异，三方参考）
     */
    private static final BigDecimal RECON_VARIANCE_UI = new BigDecimal("0.01");

    private static InvoiceKind parseInvoiceKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return InvoiceKind.ORDINARY_VAT;
        }
        return switch (raw.trim()) {
            case "ORDINARY_VAT" -> InvoiceKind.ORDINARY_VAT;
            case "SPECIAL_VAT" -> InvoiceKind.SPECIAL_VAT;
            default -> throw new BadRequestException("发票类型须为 ORDINARY_VAT（普票）或 SPECIAL_VAT（专票）");
        };
    }

    public record ReconSummary(
            Long id, String reconNo, Long supplierId, String supplierCode, String supplierName,
            LocalDate periodFrom, LocalDate periodTo,
            BigDecimal poAmount, BigDecimal grAmount, BigDecimal invoiceAmount,
            BigDecimal diffAmount, BigDecimal diffPoGrAmount, String status,
            Instant supplierConfirmedAt, Instant procurementConfirmedAt,
            boolean varianceAlert,
            String disputeReason, Instant disputedAt, String disputedBy,
            String procurementRejectReason
    ) {
        static ReconSummary from(Reconciliation r) {
            BigDecimal po = r.getPoAmount() != null ? r.getPoAmount() : BigDecimal.ZERO;
            BigDecimal gr = r.getGrAmount() != null ? r.getGrAmount() : BigDecimal.ZERO;
            BigDecimal diffPoGr = po.subtract(gr);
            BigDecimal d = r.getDiffAmount() != null ? r.getDiffAmount() : BigDecimal.ZERO;
            boolean alert = d.abs().compareTo(RECON_VARIANCE_UI) > 0;
            return new ReconSummary(r.getId(), r.getReconNo(),
                    r.getSupplier().getId(), r.getSupplier().getCode(), r.getSupplier().getName(),
                    r.getPeriodFrom(), r.getPeriodTo(),
                    r.getPoAmount(), r.getGrAmount(), r.getInvoiceAmount(),
                    r.getDiffAmount(), diffPoGr, r.getStatus().name(),
                    r.getSupplierConfirmedAt(), r.getProcurementConfirmedAt(), alert,
                    r.getDisputeReason(), r.getDisputedAt(), r.getDisputedBy(),
                    r.getProcurementRejectReason());
        }
    }
}
