package com.srm.quality.web;

import com.srm.quality.domain.CorrectiveAction;
import com.srm.quality.domain.QualityInspection;
import com.srm.quality.service.QualityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Quality", description = "质量协同")
@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    // ── Inspections ──────────────────────────────────────────────

    @GetMapping("/inspections")
    public List<InspectionResponse> listInspections(@RequestParam Long procurementOrgId) {
        return qualityService.listInspections(procurementOrgId).stream()
                .map(InspectionResponse::from)
                .toList();
    }

    @PostMapping("/inspections")
    public InspectionResponse createInspection(@Valid @RequestBody InspectionCreateRequest req) {
        QualityInspection qi = qualityService.createInspection(
                req.grId(), req.inspectionDate(), req.inspectorName(), req.result(),
                req.totalQty(), req.qualifiedQty(), req.defectQty(),
                req.defectType(), req.remark());
        return InspectionResponse.from(qi);
    }

    // ── Corrective Actions ───────────────────────────────────────

    @GetMapping("/corrective-actions")
    public List<CorrectiveActionResponse> listCorrectiveActions(@RequestParam Long procurementOrgId) {
        return qualityService.listCorrectiveActions(procurementOrgId).stream()
                .map(CorrectiveActionResponse::from)
                .toList();
    }

    @PostMapping("/corrective-actions")
    public CorrectiveActionResponse createCorrectiveAction(
            @Valid @RequestBody CorrectiveActionCreateRequest req) {
        CorrectiveAction ca = qualityService.createCorrectiveAction(
                req.inspectionId(), req.supplierId(), req.procurementOrgId(),
                req.issueDescription(), req.rootCause(), req.correctiveMeasures(),
                req.dueDate(), req.remark());
        return CorrectiveActionResponse.from(ca);
    }

    @PostMapping("/corrective-actions/{id}/close")
    public CorrectiveActionResponse closeCorrectiveAction(@PathVariable Long id) {
        return CorrectiveActionResponse.from(qualityService.closeCorrectiveAction(id));
    }

    // ── Request DTOs ─────────────────────────────────────────────

    public record InspectionCreateRequest(
            @NotNull Long grId,
            @NotNull LocalDate inspectionDate,
            String inspectorName,
            @NotNull String result,
            @NotNull BigDecimal totalQty,
            BigDecimal qualifiedQty,
            BigDecimal defectQty,
            String defectType,
            String remark
    ) {}

    public record CorrectiveActionCreateRequest(
            Long inspectionId,
            @NotNull Long supplierId,
            @NotNull Long procurementOrgId,
            @NotNull String issueDescription,
            String rootCause,
            String correctiveMeasures,
            LocalDate dueDate,
            String remark
    ) {}

    // ── Response DTOs ────────────────────────────────────────────

    public record InspectionResponse(
            Long id,
            String inspectionNo,
            Long goodsReceiptId,
            String grNo,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long procurementOrgId,
            String procurementOrgCode,
            LocalDate inspectionDate,
            String inspectorName,
            String result,
            BigDecimal totalQty,
            BigDecimal qualifiedQty,
            BigDecimal defectQty,
            String defectType,
            String remark
    ) {
        static InspectionResponse from(QualityInspection qi) {
            return new InspectionResponse(
                    qi.getId(),
                    qi.getInspectionNo(),
                    qi.getGoodsReceipt().getId(),
                    qi.getGoodsReceipt().getGrNo(),
                    qi.getSupplier().getId(),
                    qi.getSupplier().getCode(),
                    qi.getSupplier().getName(),
                    qi.getProcurementOrg().getId(),
                    qi.getProcurementOrg().getCode(),
                    qi.getInspectionDate(),
                    qi.getInspectorName(),
                    qi.getResult(),
                    qi.getTotalQty(),
                    qi.getQualifiedQty(),
                    qi.getDefectQty(),
                    qi.getDefectType(),
                    qi.getRemark()
            );
        }
    }

    public record CorrectiveActionResponse(
            Long id,
            String caNo,
            Long inspectionId,
            String inspectionNo,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long procurementOrgId,
            String procurementOrgCode,
            String issueDescription,
            String rootCause,
            String correctiveMeasures,
            LocalDate dueDate,
            String status,
            LocalDate closedDate,
            String remark
    ) {
        static CorrectiveActionResponse from(CorrectiveAction ca) {
            return new CorrectiveActionResponse(
                    ca.getId(),
                    ca.getCaNo(),
                    ca.getInspection() != null ? ca.getInspection().getId() : null,
                    ca.getInspection() != null ? ca.getInspection().getInspectionNo() : null,
                    ca.getSupplier().getId(),
                    ca.getSupplier().getCode(),
                    ca.getSupplier().getName(),
                    ca.getProcurementOrg().getId(),
                    ca.getProcurementOrg().getCode(),
                    ca.getIssueDescription(),
                    ca.getRootCause(),
                    ca.getCorrectiveMeasures(),
                    ca.getDueDate(),
                    ca.getStatus(),
                    ca.getClosedDate(),
                    ca.getRemark()
            );
        }
    }
}
