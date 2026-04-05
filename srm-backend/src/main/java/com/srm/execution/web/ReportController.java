package com.srm.execution.web;

import com.srm.execution.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
