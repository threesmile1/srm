package com.srm.invoice.web;

import com.srm.invoice.domain.*;
import com.srm.invoice.service.InvoiceService;
import com.srm.invoice.service.InvoiceService.InvoiceLineInput;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
                                              @RequestParam(required = false) Long supplierId) {
        List<Invoice> list;
        if (supplierId != null) {
            list = invoiceService.listBySupplier(supplierId);
        } else if (procurementOrgId != null) {
            list = invoiceService.listByOrg(procurementOrgId);
        } else {
            list = List.of();
        }
        return list.stream().map(InvoiceSummary::from).toList();
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDetail getInvoice(@PathVariable Long id) {
        return InvoiceDetail.from(invoiceService.requireDetail(id));
    }

    @PostMapping("/invoices")
    public InvoiceDetail createInvoice(@Valid @RequestBody InvoiceCreateRequest req) {
        List<InvoiceLineInput> lines = req.lines().stream()
                .map(l -> new InvoiceLineInput(l.materialCode(), l.materialName(),
                        l.qty(), l.unitPrice(), l.taxRate(),
                        l.purchaseOrderId(), l.purchaseOrderLineId(), l.goodsReceiptId()))
                .toList();
        Invoice inv = invoiceService.createInvoice(
                req.supplierId(), req.procurementOrgId(), req.invoiceDate(),
                req.currency(), req.taxAmount(), req.remark(), lines);
        return InvoiceDetail.from(invoiceService.requireDetail(inv.getId()));
    }

    @PostMapping("/invoices/{id}/confirm")
    public InvoiceDetail confirmInvoice(@PathVariable Long id) {
        invoiceService.confirmInvoice(id);
        return InvoiceDetail.from(invoiceService.requireDetail(id));
    }

    @PostMapping("/invoices/{id}/reject")
    public InvoiceDetail rejectInvoice(@PathVariable Long id,
                                        @RequestBody(required = false) RejectRequest req) {
        invoiceService.rejectInvoice(id, req != null ? req.reason() : null);
        return InvoiceDetail.from(invoiceService.requireDetail(id));
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
                req.periodFrom(), req.periodTo(), req.remark());
        return ReconSummary.from(recon);
    }

    @PostMapping("/reconciliations/{id}/confirm")
    public ReconSummary confirmRecon(@PathVariable Long id) {
        return ReconSummary.from(invoiceService.confirmRecon(id));
    }

    // --- DTOs ---

    public record InvoiceCreateRequest(
            @NotNull Long supplierId,
            @NotNull Long procurementOrgId,
            @NotNull LocalDate invoiceDate,
            String currency,
            BigDecimal taxAmount,
            String remark,
            @NotEmpty List<InvLineReq> lines
    ) {}

    public record InvLineReq(
            String materialCode, String materialName,
            @NotNull BigDecimal qty, @NotNull BigDecimal unitPrice,
            BigDecimal taxRate,
            Long purchaseOrderId, Long purchaseOrderLineId, Long goodsReceiptId
    ) {}

    public record RejectRequest(String reason) {}

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
            String currency, String status
    ) {
        static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getInvoiceNo(),
                    i.getSupplier().getId(), i.getSupplier().getCode(), i.getSupplier().getName(),
                    i.getInvoiceDate(), i.getTotalAmount(), i.getTaxAmount(),
                    i.getCurrency(), i.getStatus().name());
        }
    }

    public record InvoiceDetail(
            Long id, String invoiceNo, Long supplierId, String supplierCode, String supplierName,
            Long procurementOrgId, String procurementOrgCode,
            LocalDate invoiceDate, BigDecimal totalAmount, BigDecimal taxAmount,
            String currency, String status, String remark,
            List<InvLineResponse> lines
    ) {
        static InvoiceDetail from(Invoice i) {
            return new InvoiceDetail(i.getId(), i.getInvoiceNo(),
                    i.getSupplier().getId(), i.getSupplier().getCode(), i.getSupplier().getName(),
                    i.getProcurementOrg().getId(), i.getProcurementOrg().getCode(),
                    i.getInvoiceDate(), i.getTotalAmount(), i.getTaxAmount(),
                    i.getCurrency(), i.getStatus().name(), i.getRemark(),
                    i.getLines().stream().map(InvLineResponse::from).toList());
        }
    }

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

    public record ReconSummary(
            Long id, String reconNo, Long supplierId, String supplierCode, String supplierName,
            LocalDate periodFrom, LocalDate periodTo,
            BigDecimal poAmount, BigDecimal grAmount, BigDecimal invoiceAmount,
            BigDecimal diffAmount, String status
    ) {
        static ReconSummary from(Reconciliation r) {
            return new ReconSummary(r.getId(), r.getReconNo(),
                    r.getSupplier().getId(), r.getSupplier().getCode(), r.getSupplier().getName(),
                    r.getPeriodFrom(), r.getPeriodTo(),
                    r.getPoAmount(), r.getGrAmount(), r.getInvoiceAmount(),
                    r.getDiffAmount(), r.getStatus().name());
        }
    }
}
