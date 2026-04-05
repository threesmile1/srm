package com.srm.foundation.web;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.service.FoundationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Foundation", description = "A1 账套 / 组织 / 仓库")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FoundationController {

    private final FoundationService foundationService;

    @Operation(summary = "健康检查")
    @GetMapping("/public/ping")
    public String ping() {
        return "ok";
    }

    @GetMapping("/ledgers")
    public List<LedgerDto> listLedgers() {
        return foundationService.listLedgers().stream().map(LedgerDto::from).toList();
    }

    @PostMapping("/ledgers")
    public LedgerDto createLedger(@Valid @RequestBody LedgerDto.CreateRequest req) {
        Ledger saved = foundationService.createLedger(req.code(), req.name(), req.u9LedgerCode());
        return LedgerDto.from(saved);
    }

    @GetMapping("/ledgers/{ledgerId}/org-units")
    public List<OrgUnitDto> listOrgUnits(@PathVariable Long ledgerId) {
        return foundationService.listOrgUnits(ledgerId).stream().map(OrgUnitDto::from).toList();
    }

    @PostMapping("/ledgers/{ledgerId}/org-units")
    public OrgUnitDto createOrgUnit(
            @PathVariable Long ledgerId,
            @Valid @RequestBody OrgUnitDto.CreateRequest req
    ) {
        OrgUnit saved = foundationService.createOrgUnit(
                ledgerId, req.orgType(), req.code(), req.name(), req.u9OrgCode());
        return OrgUnitDto.from(saved);
    }

    @GetMapping("/org-units/{orgId}/warehouses")
    public List<WarehouseDto> listWarehouses(@PathVariable Long orgId) {
        return foundationService.listWarehouses(orgId).stream().map(WarehouseDto::from).toList();
    }

    @PostMapping("/org-units/{orgId}/warehouses")
    public WarehouseDto createWarehouse(
            @PathVariable Long orgId,
            @Valid @RequestBody WarehouseDto.CreateRequest req
    ) {
        Warehouse saved = foundationService.createWarehouse(
                orgId, req.code(), req.name(), req.u9WhCode());
        return WarehouseDto.from(saved);
    }
}
