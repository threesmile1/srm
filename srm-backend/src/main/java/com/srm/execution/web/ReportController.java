package com.srm.execution.web;

import com.srm.execution.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Reports", description = "A8 报表")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/purchase-execution")
    public List<ReportService.PurchaseExecutionRow> purchaseExecution(@RequestParam Long procurementOrgId) {
        return reportService.purchaseExecution(procurementOrgId);
    }

    @GetMapping("/analytics/purchase-amount-trend")
    public List<ReportService.MonthAmountRow> purchaseAmountTrend(
            @RequestParam Long procurementOrgId,
            @RequestParam(defaultValue = "12") int months) {
        return reportService.purchaseAmountTrend(procurementOrgId, months);
    }

    @GetMapping("/analytics/supplier-share")
    public List<ReportService.SupplierShareRow> supplierShare(
            @RequestParam Long procurementOrgId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return reportService.supplierShare(procurementOrgId, from, to);
    }

    @GetMapping("/analytics/delivery-achievement")
    public ReportService.DeliveryAchievementRow deliveryAchievement(@RequestParam Long procurementOrgId) {
        return reportService.deliveryAchievement(procurementOrgId);
    }

    @GetMapping("/analytics/price-analysis")
    public List<ReportService.PriceAnalysisRow> priceAnalysis(
            @RequestParam Long procurementOrgId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(defaultValue = "20") int limit) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(365);
        return reportService.priceAnalysis(procurementOrgId, f, limit);
    }
}
