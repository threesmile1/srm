package com.srm.foundation.web;

import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.repo.ApprovalInstanceRepository;
import com.srm.invoice.domain.InvoiceStatus;
import com.srm.invoice.repo.InvoiceRepository;
import com.srm.notification.service.NotificationService;
import com.srm.perf.domain.EvalStatus;
import com.srm.perf.domain.PerfEvaluation;
import com.srm.perf.repo.PerfEvaluationRepository;
import com.srm.po.domain.PoStatus;
import com.srm.po.repo.PurchaseOrderRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Dashboard", description = "工作台看板")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PerfEvaluationRepository perfEvaluationRepository;
    private final NotificationService notificationService;

    @GetMapping("/stats")
    public DashboardStats stats(@RequestParam Long procurementOrgId, HttpServletRequest httpReq) {
        long pendingApprovals = approvalInstanceRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING).size();
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        BigDecimal monthPoAmount = purchaseOrderRepository.sumAmountBySupplierAndOrgAndPeriod(
                null, procurementOrgId, monthStart, now);

        long pendingInvoices = invoiceRepository.countByProcurementOrgIdAndStatus(
                procurementOrgId, InvoiceStatus.SUBMITTED);
        long pendingReceiveLines = purchaseOrderRepository.countOpenReceiveLinesByProcurementOrg(
                procurementOrgId, PoStatus.RELEASED);

        long unreadUserNotifications = 0L;
        HttpSession session = httpReq.getSession(false);
        if (session != null) {
            Object uid = session.getAttribute(AuthController.SESSION_USER_ID);
            if (uid instanceof Long lid) {
                unreadUserNotifications = notificationService.unreadCountForUser(lid);
            } else if (uid instanceof Number n) {
                unreadUserNotifications = notificationService.unreadCountForUser(n.longValue());
            }
        }

        return new DashboardStats(pendingApprovals,
                monthPoAmount != null ? monthPoAmount : BigDecimal.ZERO,
                pendingReceiveLines, pendingInvoices, unreadUserNotifications);
    }

    @GetMapping("/po-trend")
    public List<MonthAmount> poTrend(@RequestParam Long procurementOrgId) {
        List<MonthAmount> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal amt = purchaseOrderRepository.sumAmountBySupplierAndOrgAndPeriod(
                    null, procurementOrgId, from, to);
            result.add(new MonthAmount(ym.toString(), amt != null ? amt : BigDecimal.ZERO));
        }
        return result;
    }

    @GetMapping("/perf-distribution")
    public List<GradeCount> perfDistribution() {
        List<PerfEvaluation> evals = perfEvaluationRepository.findAllByOrderByIdDesc();
        List<PerfEvaluation> published = evals.stream()
                .filter(e -> e.getStatus() == EvalStatus.PUBLISHED && e.getGrade() != null)
                .toList();
        Map<String, Long> gradeMap = published.stream()
                .collect(Collectors.groupingBy(PerfEvaluation::getGrade, LinkedHashMap::new, Collectors.counting()));
        return gradeMap.entrySet().stream()
                .map(e -> new GradeCount(e.getKey(), e.getValue()))
                .toList();
    }

    @GetMapping("/pending-items")
    public List<PendingItem> pendingItems() {
        return approvalInstanceRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING).stream()
                .limit(10)
                .map(ai -> new PendingItem(ai.getId(), ai.getDocType(), ai.getDocNo(),
                        ai.getTotalAmount(), ai.getCreatedAt().toString()))
                .toList();
    }

    public record DashboardStats(long pendingApprovals, BigDecimal monthPoAmount,
                                  long pendingReceiveLines, long pendingInvoices,
                                  long unreadUserNotifications) {}
    public record MonthAmount(String month, BigDecimal amount) {}
    public record GradeCount(String grade, long count) {}
    public record PendingItem(Long instanceId, String docType, String docNo,
                               BigDecimal amount, String createdAt) {}
}
