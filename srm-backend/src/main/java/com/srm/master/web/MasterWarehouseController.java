package com.srm.master.web;

import com.srm.master.service.MaterialDerivedMasterService;
import com.srm.master.service.WarehouseMasterFromMaterialService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仓库主数据：列表数据来自 {@code warehouse} 表（由物料四厂仓同步写入）；物料条数为引用该仓编码的物料数。
 */
@Tag(name = "MasterWarehouse", description = "主数据：仓库（来自物料聚合）")
@RestController
@RequestMapping("/api/v1/master/warehouses")
@RequiredArgsConstructor
public class MasterWarehouseController {

    private final MaterialDerivedMasterService materialDerivedMasterService;
    private final WarehouseMasterFromMaterialService warehouseMasterFromMaterialService;

    @GetMapping
    public Page<MaterialDerivedMasterService.MaterialWarehouseRefRow> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int ps = normalizePageSize(size);
        return materialDerivedMasterService.pageWarehouseRefs(PageRequest.of(Math.max(0, page), ps));
    }

    /**
     * 按当前物料表四厂仓字段全量 upsert {@code warehouse}（可能较慢，仅补历史或运维使用）。
     */
    @PostMapping("/rebuild-from-materials")
    public Map<String, String> rebuildFromMaterials() {
        warehouseMasterFromMaterialService.rebuildWarehousesFromAllMaterials();
        return Map.of("status", "ok");
    }

    private static int normalizePageSize(int size) {
        if (size == 20 || size == 50) {
            return size;
        }
        return 10;
    }
}
