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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /**
     * 可对账订单行（已收 − 已开票 &gt; 0），用于甄云类「从订单行勾选」开票明细。
     */
    @GetMapping("/billable-lines")
    public List<BillablePoLineResponse> billableLines(@RequestParam Long procurementOrgId, HttpSession session) {
        Long sid = requireSupplierId(session);
        return invoiceService.listBillablePoLinesForSupplier(sid, procurementOrgId).stream()
                .map(BillablePoLineResponse::from)
                .toList();
    }

    /** OSIV 关闭时，须在事务内完成 DTO 映射，避免 InvoiceDetail.from 触发懒加载异常 */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public InvoiceController.InvoiceDetail get(@PathVariable Long id, HttpSession session) {
        Long sid = requireSupplierId(session);
        Invoice inv = invoiceService.requireDetail(id);
        if (!inv.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看此发票");
        }
        return InvoiceController.InvoiceDetail.from(inv);
    }

    @PostMapping
    @Transactional
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

    /** 提交发票成功后上传 PDF/图片等扫描件（单文件 ≤10MB，可多次上传） */
    @PostMapping("/{invoiceId}/attachments")
    public InvoiceController.InvoiceAttachmentItem uploadAttachment(@PathVariable Long invoiceId,
                                                                   @RequestParam("file") MultipartFile file,
                                                                   HttpSession session) throws IOException {
        Long sid = requireSupplierId(session);
        InvoiceService.InvoiceAttachmentBrief b = invoiceService.addPortalInvoiceAttachment(invoiceId, sid, file);
        return new InvoiceController.InvoiceAttachmentItem(b.id(), b.originalName(), b.contentType(), b.fileSize());
    }

    @GetMapping("/{invoiceId}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long invoiceId,
                                                        @PathVariable Long attachmentId,
                                                        HttpSession session) {
        Long sid = requireSupplierId(session);
        InvoiceService.InvoiceAttachmentDownload d =
                invoiceService.openInvoiceAttachmentDownload(invoiceId, attachmentId, sid);
        return InvoiceController.attachmentResponse(d);
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

    public record BillablePoLineResponse(
            Long purchaseOrderLineId,
            Long purchaseOrderId,
            String poNo,
            int lineNo,
            String materialCode,
            String materialName,
            BigDecimal receivedQty,
            BigDecimal invoicedQty,
            BigDecimal remainingInvoiceableQty,
            BigDecimal unitPrice,
            String uom
    ) {
        static BillablePoLineResponse from(InvoiceService.BillablePoLineRow r) {
            return new BillablePoLineResponse(
                    r.purchaseOrderLineId(),
                    r.purchaseOrderId(),
                    r.poNo(),
                    r.lineNo(),
                    r.materialCode(),
                    r.materialName(),
                    r.receivedQty(),
                    r.invoicedQty(),
                    r.remainingInvoiceableQty(),
                    r.unitPrice(),
                    r.uom());
        }
    }
}
