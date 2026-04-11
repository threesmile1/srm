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
 * 仓库主数据：由物料表中的仓库字段聚合（U9 仓名 + 四厂仓），不再从 cangku.cpt 同步独立仓库主档。
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
