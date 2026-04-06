package com.srm.master.web;

import com.srm.master.domain.Supplier;
import com.srm.master.domain.SupplierAudit;
import com.srm.master.domain.SupplierLifecycleStatus;
import com.srm.master.service.SupplierLifecycleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "SupplierLifecycle", description = "供应商生命周期管理")
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierLifecycleController {

    private final SupplierLifecycleService supplierLifecycleService;

    @PostMapping("/{id}/lifecycle-status")
    public SupplierStatusResponse updateLifecycleStatus(
            @PathVariable Long id,
            @Valid @RequestBody LifecycleStatusRequest req) {
        Supplier s = supplierLifecycleService.updateLifecycleStatus(id, req.status());
        return SupplierStatusResponse.from(s);
    }

    @GetMapping("/{id}/audits")
    public List<AuditResponse> listAudits(@PathVariable Long id) {
        return supplierLifecycleService.listAudits(id).stream()
                .map(AuditResponse::from)
                .toList();
    }

    @PostMapping("/{id}/audits")
    public AuditResponse addAudit(@PathVariable Long id, @Valid @RequestBody AuditCreateRequest req) {
        SupplierAudit audit = supplierLifecycleService.addAudit(
                id, req.auditType(), req.auditDate(), req.result(),
                req.score(), req.auditorName(), req.remark());
        return AuditResponse.from(audit);
    }

    public record LifecycleStatusRequest(@NotNull SupplierLifecycleStatus status) {}

    public record SupplierStatusResponse(Long id, String code, String name, String lifecycleStatus) {
        static SupplierStatusResponse from(Supplier s) {
            return new SupplierStatusResponse(
                    s.getId(), s.getCode(), s.getName(), s.getLifecycleStatus().name());
        }
    }

    public record AuditCreateRequest(
            @NotBlank String auditType,
            @NotNull LocalDate auditDate,
            @NotBlank String result,
            Integer score,
            String auditorName,
            String remark
    ) {}

    public record AuditResponse(
            Long id,
            Long supplierId,
            String auditType,
            LocalDate auditDate,
            String result,
            Integer score,
            String auditorName,
            String remark
    ) {
        static AuditResponse from(SupplierAudit a) {
            return new AuditResponse(
                    a.getId(),
                    a.getSupplier().getId(),
                    a.getAuditType(),
                    a.getAuditDate(),
                    a.getResult(),
                    a.getScore(),
                    a.getAuditorName(),
                    a.getRemark());
        }
    }
}
