package com.srm.invoice.web;

import com.srm.foundation.web.AuthController;
import com.srm.invoice.domain.Reconciliation;
import com.srm.invoice.service.InvoiceService;
import com.srm.web.error.ForbiddenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "PortalReconciliation", description = "供应商门户 - 对账")
@RestController
@RequestMapping("/api/v1/portal/reconciliations")
@RequiredArgsConstructor
public class PortalReconciliationController {

    private final InvoiceService invoiceService;

    private Long requireSupplierId(HttpSession session) {
        Long sid = (Long) session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid == null) {
            throw new ForbiddenException("当前用户非供应商账号");
        }
        return sid;
    }

    @GetMapping
    public List<InvoiceController.ReconSummary> list(HttpSession session) {
        Long sid = requireSupplierId(session);
        return invoiceService.listReconBySupplier(sid).stream()
                .map(InvoiceController.ReconSummary::from)
                .toList();
    }

    /**
     * 甄云类：月末由供应商在门户发起对账，生成后状态为「待采购确认」。
     */
    @PostMapping
    public InvoiceController.ReconSummary create(@Valid @RequestBody PortalReconCreateRequest req,
                                                   HttpSession session) {
        Long sid = requireSupplierId(session);
        Reconciliation r = invoiceService.createReconciliation(
                sid, req.procurementOrgId(),
                req.periodFrom(), req.periodTo(), req.remark(), true);
        return InvoiceController.ReconSummary.from(r);
    }

    @PostMapping("/{id}/supplier-confirm")
    public InvoiceController.ReconSummary supplierConfirm(@PathVariable Long id, HttpSession session) {
        Long sid = requireSupplierId(session);
        Reconciliation r = invoiceService.confirmReconciliationBySupplier(id, sid);
        return InvoiceController.ReconSummary.from(r);
    }

    @PostMapping("/{id}/supplier-dispute")
    public InvoiceController.ReconSummary supplierDispute(@PathVariable Long id,
                                                          @Valid @RequestBody InvoiceController.ReconReasonRequest req,
                                                          HttpSession session) {
        Long sid = requireSupplierId(session);
        Reconciliation r = invoiceService.supplierDisputeReconciliation(id, sid, req.reason());
        return InvoiceController.ReconSummary.from(r);
    }

    /**
     * 撤回自行发起、待采购尚未处理的对账单（误发起时可删除后重新发起）。
     */
    @PostMapping("/{id}/withdraw")
    public InvoiceController.ReconSummary withdraw(@PathVariable Long id, HttpSession session) {
        Long sid = requireSupplierId(session);
        Reconciliation r = invoiceService.supplierWithdrawReconciliation(id, sid);
        return InvoiceController.ReconSummary.from(r);
    }

    public record PortalReconCreateRequest(
            @NotNull Long procurementOrgId,
            @NotNull LocalDate periodFrom,
            @NotNull LocalDate periodTo,
            String remark
    ) {}
}
