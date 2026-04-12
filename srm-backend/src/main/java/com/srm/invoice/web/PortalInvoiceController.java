package com.srm.invoice.web;

import com.srm.foundation.web.AuthController;
import com.srm.invoice.domain.Invoice;
import com.srm.invoice.domain.InvoiceKind;
import com.srm.web.error.BadRequestException;
import com.srm.invoice.service.InvoiceService;
import com.srm.invoice.service.InvoiceService.InvoiceLineInput;
import com.srm.web.error.ForbiddenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "PortalInvoice", description = "供应商门户 - 发票")
@RestController
@RequestMapping("/api/v1/portal/invoices")
@RequiredArgsConstructor
public class PortalInvoiceController {

    private final InvoiceService invoiceService;

    private Long requireSupplierId(HttpSession session) {
        Long sid = (Long) session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid == null) throw new ForbiddenException("当前用户非供应商账号");
        return sid;
    }

    @GetMapping
    public List<InvoiceController.InvoiceSummary> list(HttpSession session) {
        Long sid = requireSupplierId(session);
        return invoiceService.listBySupplier(sid).stream()
                .map(InvoiceController.InvoiceSummary::from).toList();
    }

    @GetMapping("/{id}")
    public InvoiceController.InvoiceDetail get(@PathVariable Long id, HttpSession session) {
        Long sid = requireSupplierId(session);
        Invoice inv = invoiceService.requireDetail(id);
        if (!inv.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看此发票");
        }
        return InvoiceController.InvoiceDetail.from(inv);
    }

    @PostMapping
    public InvoiceController.InvoiceDetail create(@Valid @RequestBody PortalInvoiceCreateReq req,
                                                    HttpSession session) {
        Long sid = requireSupplierId(session);
        List<InvoiceLineInput> lines = req.lines().stream()
                .map(l -> new InvoiceLineInput(l.materialCode(), l.materialName(),
                        l.qty(), l.unitPrice(), l.taxRate(),
                        l.purchaseOrderId(), l.purchaseOrderLineId(), l.goodsReceiptId()))
                .toList();
        Invoice inv = invoiceService.createInvoice(
                sid, req.procurementOrgId(), req.invoiceDate(),
                req.currency(), req.taxAmount(), req.remark(),
                parseInvoiceKind(req.invoiceKind()),
                req.vatInvoiceCode(), req.vatInvoiceNumber(),
                lines);
        return InvoiceController.InvoiceDetail.from(invoiceService.requireDetail(inv.getId()));
    }

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

    public record PortalInvoiceCreateReq(
            @NotNull Long procurementOrgId,
            @NotNull LocalDate invoiceDate,
            String currency,
            BigDecimal taxAmount,
            String remark,
            String invoiceKind,
            String vatInvoiceCode,
            String vatInvoiceNumber,
            @NotEmpty List<InvoiceController.InvLineReq> lines
    ) {}
}
