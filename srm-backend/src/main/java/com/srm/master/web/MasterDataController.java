package com.srm.master.web;

import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "MasterData", description = "A2 主数据：供应商、物料")
@RestController
@RequestMapping("/api/v1/master")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    @GetMapping("/suppliers")
    public List<SupplierResponse> listSuppliers() {
        return masterDataService.listSuppliers().stream().map(SupplierResponse::from).toList();
    }

    @PostMapping("/suppliers")
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierCreateRequest req) {
        Supplier s = masterDataService.createSupplier(
                req.code(), req.name(), req.u9VendorCode(), req.taxId(), req.procurementOrgIds());
        return SupplierResponse.from(masterDataService.requireSupplier(s.getId()));
    }

    @PutMapping("/suppliers/{id}")
    public SupplierResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest req) {
        masterDataService.updateSupplier(id, req.name(), req.u9VendorCode(), req.taxId(), req.procurementOrgIds());
        return SupplierResponse.from(masterDataService.requireSupplier(id));
    }

    @GetMapping("/materials")
    public List<MaterialResponse> listMaterials() {
        return masterDataService.listMaterials().stream().map(MaterialResponse::from).toList();
    }

    @PostMapping("/materials")
    public MaterialResponse createMaterial(@Valid @RequestBody MaterialCreateRequest req) {
        MaterialItem m = masterDataService.createMaterial(req.code(), req.name(), req.uom(), req.u9ItemCode());
        return MaterialResponse.from(m);
    }

    @PutMapping("/materials/{id}")
    public MaterialResponse updateMaterial(@PathVariable Long id, @Valid @RequestBody MaterialUpdateRequest req) {
        return MaterialResponse.from(
                masterDataService.updateMaterial(id, req.name(), req.uom(), req.u9ItemCode()));
    }

    public record SupplierResponse(
            Long id,
            String code,
            String name,
            String u9VendorCode,
            String taxId,
            Set<Long> procurementOrgIds
    ) {
        static SupplierResponse from(Supplier s) {
            return new SupplierResponse(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getU9VendorCode(),
                    s.getTaxId(),
                    s.getAuthorizedProcurementOrgs().stream().map(com.srm.foundation.domain.OrgUnit::getId)
                            .collect(Collectors.toSet())
            );
        }
    }

    public record SupplierCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String u9VendorCode,
            @Size(max = 64) String taxId,
            Set<Long> procurementOrgIds
    ) {}

    public record SupplierUpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String u9VendorCode,
            @Size(max = 64) String taxId,
            Set<Long> procurementOrgIds
    ) {}

    public record MaterialResponse(Long id, String code, String name, String uom, String u9ItemCode) {
        static MaterialResponse from(MaterialItem m) {
            return new MaterialResponse(m.getId(), m.getCode(), m.getName(), m.getUom(), m.getU9ItemCode());
        }
    }

    public record MaterialCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 32) String uom,
            @Size(max = 64) String u9ItemCode
    ) {}

    public record MaterialUpdateRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 32) String uom,
            @Size(max = 64) String u9ItemCode
    ) {}
}
