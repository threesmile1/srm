package com.srm.master.web;

import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.integration.u9.U9MaterialSyncJobRegistry;
import com.srm.integration.u9.U9MaterialSyncJobRunner;
import com.srm.integration.u9.U9MaterialSyncJobStatus;
import com.srm.integration.u9.U9MaterialSyncRow;
import com.srm.integration.u9.U9MaterialFactoryWarehouseSyncService;
import com.srm.integration.u9.U9MaterialSyncService;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import com.srm.master.service.MasterDataImportService;
import com.srm.master.service.MasterDataImportService.ImportResult;
import com.srm.master.service.MasterDataService;
import com.srm.master.service.MaterialDerivedMasterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "MasterData", description = "A2 主数据：供应商、物料")
@RestController
@RequestMapping("/api/v1/master")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final MasterDataImportService importService;
    private final U9MaterialSyncService u9MaterialSyncService;
    private final U9MaterialSyncJobRegistry u9MaterialSyncJobRegistry;
    private final U9MaterialSyncJobRunner u9MaterialSyncJobRunner;
    private final U9MaterialFactoryWarehouseSyncService u9MaterialFactoryWarehouseSyncService;
    private final MaterialDerivedMasterService materialDerivedMasterService;

    @GetMapping("/suppliers")
    public List<SupplierResponse> listSuppliers() {
        return masterDataService.listSuppliers().stream().map(SupplierResponse::from).toList();
    }

    /**
     * 物料中出现的供应商（分页聚合，与 {@link #listSuppliers()} 同源维度）；列表展示用。
     */
    @GetMapping("/suppliers/material-derived")
    public Page<MaterialDerivedMasterService.MaterialSupplierRefRow> listSuppliersFromMaterials(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int ps = normalizeMaterialPageSize(size);
        return materialDerivedMasterService.pageSupplierRefs(PageRequest.of(Math.max(0, page), ps));
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

    @PostMapping("/suppliers/import")
    public ImportResult importSuppliers(@RequestParam("file") MultipartFile file) {
        return importService.importSuppliers(file);
    }

    @PostMapping("/materials/import")
    @SuppressWarnings("unused")
    public ImportResult importMaterials(@RequestParam("file") MultipartFile file) {
        throw new BadRequestException("物料仅允许通过 U9 同步，不支持 Excel 导入");
    }

    /**
     * U9 物料同步（wuliao.cpt 等）。无请求体时从配置 URL 拉取 JSON；有请求体时直接落库（便于网关/手工推送）。
     */
    @PostMapping("/materials/sync-from-u9")
    public U9MaterialSyncService.U9MaterialSyncResult syncMaterialsFromU9(
            @RequestBody(required = false) List<U9MaterialSyncRow> body) {
        if (body != null) {
            return u9MaterialSyncService.apply(body);
        }
        return u9MaterialSyncService.fetchAndApply();
    }

    /**
     * 异步全量同步：立即返回 jobId，轮询 {@link #getU9SyncJob(String)} 直至 SUCCESS/FAILED。
     */
    @PostMapping("/materials/sync-from-u9/async")
    public Map<String, String> startSyncMaterialsFromU9Async() {
        String jobId = u9MaterialSyncJobRegistry.createJob();
        u9MaterialSyncJobRunner.runAsync(jobId);
        return Map.of("jobId", jobId);
    }

    @GetMapping("/materials/sync-from-u9/jobs/{jobId}")
    public U9MaterialSyncJobStatus getU9SyncJob(@PathVariable String jobId) {
        return u9MaterialSyncJobRegistry.get(jobId)
                .orElseThrow(() -> new NotFoundException("同步任务不存在: " + jobId));
    }

    /**
     * 从帆软 cangku_yigui / cangku_shuiqi 按料号拉取各厂默认仓（每料号传 parameters.code，见 srm.u9.factory-warehouse-report-parameter-name）。
     * 请求体为物料编码列表时只同步这些编码；省略或空数组表示本地全部物料。
     */
    @PostMapping("/materials/sync-factory-warehouses-from-u9")
    public U9MaterialFactoryWarehouseSyncService.FactoryWarehouseSyncResult syncFactoryWarehousesFromU9(
            @RequestBody(required = false) List<String> materialCodes) {
        return u9MaterialFactoryWarehouseSyncService.syncFromU9(materialCodes);
    }

    /**
     * 批量 lpgys：请求体为物料编码列表时只同步这些料号；省略或空数组表示本地全部物料。
     */
    @PostMapping("/materials/sync-suppliers-from-u9")
    public U9MaterialSyncService.U9LpgysBulkSyncResult syncSuppliersFromLpgysBulk(
            @RequestBody(required = false) List<String> materialCodes) {
        return u9MaterialSyncService.syncSuppliersFromLpgys(materialCodes);
    }

    /**
     * 仅对一条物料调用帆软 lpgys，写入 material_supplier_u9 与物料表首供快照（不跑 wuliao 全量）。
     */
    @PostMapping("/materials/{materialId}/sync-suppliers-from-u9")
    public U9MaterialSyncService.U9LpgysSingleSyncResult syncSuppliersFromU9ForMaterial(
            @PathVariable Long materialId) {
        return u9MaterialSyncService.syncSuppliersFromLpgysForMaterialId(materialId);
    }

    /**
     * 物料分页列表（管理端表格）。page 从 0 起；size 仅允许 10 / 20 / 50，默认 10。
     */
    @GetMapping("/materials")
    public Page<MaterialResponse> listMaterials(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int ps = normalizeMaterialPageSize(size);
        return masterDataService
                .pageMaterials(PageRequest.of(Math.max(0, page), ps, Sort.by("code").ascending()))
                .map(MaterialResponse::from);
    }

    /** 下拉框等需全量物料时使用（无分页） */
    @GetMapping("/materials/all")
    public List<MaterialResponse> listAllMaterials() {
        return masterDataService.listMaterials().stream().map(MaterialResponse::from).toList();
    }

    private static int normalizeMaterialPageSize(int size) {
        if (size == 20 || size == 50) {
            return size;
        }
        return 10;
    }

    @PostMapping("/materials")
    @SuppressWarnings("unused")
    public MaterialResponse createMaterial(@Valid @RequestBody MaterialCreateRequest req) {
        throw new BadRequestException("物料仅允许通过 U9 同步，不支持在系统中新建");
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
            String lifecycleStatus,
            Set<Long> procurementOrgIds
    ) {
        static SupplierResponse from(Supplier s) {
            return new SupplierResponse(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getU9VendorCode(),
                    s.getTaxId(),
                    s.getLifecycleStatus() != null ? s.getLifecycleStatus().name() : null,
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

    public record MaterialResponse(
            Long id,
            String code,
            String name,
            String uom,
            String u9ItemCode,
            String specification,
            BigDecimal purchaseUnitPrice,
            String u9WarehouseSuzhou,
            String u9WarehouseChengdu,
            String u9WarehouseHuanan,
            String u9WarehouseShuiqi,
            String u9SupplierCode,
            String u9SupplierName
    ) {
        static MaterialResponse from(MaterialItem m) {
            return new MaterialResponse(
                    m.getId(),
                    m.getCode(),
                    m.getName(),
                    m.getUom(),
                    m.getU9ItemCode(),
                    m.getSpecification(),
                    m.getPurchaseUnitPrice(),
                    m.getU9WarehouseSuzhou(),
                    m.getU9WarehouseChengdu(),
                    m.getU9WarehouseHuanan(),
                    m.getU9WarehouseShuiqi(),
                    m.getU9SupplierCode(),
                    m.getU9SupplierName());
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
