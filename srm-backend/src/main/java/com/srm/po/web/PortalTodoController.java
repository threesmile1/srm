package com.srm.po.web;

import com.srm.execution.repo.AsnNoticeRepository;
import com.srm.po.domain.PoStatus;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.foundation.web.PortalSupplierSession;
import com.srm.rfq.domain.RfqStatus;
import com.srm.rfq.repo.RfqRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "PortalTodo", description = "门户待办摘要")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalTodoController {

    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final AsnNoticeRepository asnNoticeRepository;
    private final RfqRepository rfqRepository;

    @Transactional(readOnly = true)
    @GetMapping("/todo-summary")
    public TodoSummary todoSummary(
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        long pendingConfirmLines = purchaseOrderLineRepository.countUnconfirmedLinesForSupplierReleasedOrders(
                sid, PoStatus.RELEASED);
        long asnNoticeCount = asnNoticeRepository.countBySupplier_Id(sid);
        long pendingRfqQuotations = rfqRepository.countAwaitingSupplierQuotation(
                sid, RfqStatus.PUBLISHED, LocalDate.now());
        return new TodoSummary(pendingConfirmLines, asnNoticeCount, pendingRfqQuotations);
    }

    public record TodoSummary(long pendingConfirmLines, long asnNoticeCount, long pendingRfqQuotations) {}
}
