package com.srm.master.web;

import com.srm.master.service.MaterialDerivedMasterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仓库主数据：物料四厂仓字段聚合出仓库编码（采购组织：苏州/成都/华南/水漆工厂），名称由 cangku.cpt 按编码解析。
 */
@Tag(name = "MasterWarehouse", description = "主数据：仓库（来自物料聚合）")
@RestController
@RequestMapping("/api/v1/master/warehouses")
@RequiredArgsConstructor
public class MasterWarehouseController {

    private final MaterialDerivedMasterService materialDerivedMasterService;

    @GetMapping
    public Page<MaterialDerivedMasterService.MaterialWarehouseRefRow> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int ps = normalizePageSize(size);
        return materialDerivedMasterService.pageWarehouseRefs(PageRequest.of(Math.max(0, page), ps));
    }

    private static int normalizePageSize(int size) {
        if (size == 20 || size == 50) {
            return size;
        }
        return 10;
    }
}
