package com.srm.master.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 从物料主数据聚合「仓库名 / 供应商」引用，替代独立 U9 仓库、lpgys 同步展示。
 */
@Service
@RequiredArgsConstructor
public class MaterialDerivedMasterService {

    private final JdbcTemplate jdbcTemplate;

    public record MaterialWarehouseRefRow(String scope, String warehouseName, long materialCount) {}

    public record MaterialSupplierRefRow(String supplierCode, String supplierName, long refCount) {}

    @Transactional(readOnly = true)
    public Page<MaterialWarehouseRefRow> pageWarehouseRefs(Pageable pageable) {
        String union = """
                SELECT 'U9' AS scope_label, TRIM(u9_warehouse_name) AS warehouse_name, COUNT(*) AS material_count
                FROM material_item
                WHERE u9_warehouse_name IS NOT NULL AND CHAR_LENGTH(TRIM(u9_warehouse_name)) > 0
                GROUP BY TRIM(u9_warehouse_name)
                UNION ALL
                SELECT '苏州', TRIM(warehouse_suzhou), COUNT(*)
                FROM material_item
                WHERE warehouse_suzhou IS NOT NULL AND CHAR_LENGTH(TRIM(warehouse_suzhou)) > 0
                GROUP BY TRIM(warehouse_suzhou)
                UNION ALL
                SELECT '成都', TRIM(warehouse_chengdu), COUNT(*)
                FROM material_item
                WHERE warehouse_chengdu IS NOT NULL AND CHAR_LENGTH(TRIM(warehouse_chengdu)) > 0
                GROUP BY TRIM(warehouse_chengdu)
                UNION ALL
                SELECT '华南', TRIM(warehouse_huanan), COUNT(*)
                FROM material_item
                WHERE warehouse_huanan IS NOT NULL AND CHAR_LENGTH(TRIM(warehouse_huanan)) > 0
                GROUP BY TRIM(warehouse_huanan)
                UNION ALL
                SELECT '水漆', TRIM(warehouse_shuiqi), COUNT(*)
                FROM material_item
                WHERE warehouse_shuiqi IS NOT NULL AND CHAR_LENGTH(TRIM(warehouse_shuiqi)) > 0
                GROUP BY TRIM(warehouse_shuiqi)
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + union + ") t", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String paged = "SELECT scope_label, warehouse_name, material_count FROM (" + union
                + ") t ORDER BY scope_label, warehouse_name LIMIT ? OFFSET ?";
        List<MaterialWarehouseRefRow> content = jdbcTemplate.query(
                paged,
                (rs, rowNum) -> new MaterialWarehouseRefRow(
                        rs.getString("scope_label"),
                        rs.getString("warehouse_name"),
                        rs.getLong("material_count")),
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, tot);
    }

    @Transactional(readOnly = true)
    public Page<MaterialSupplierRefRow> pageSupplierRefs(Pageable pageable) {
        String union = """
                SELECT supplier_code, MAX(supplier_name) AS supplier_name, SUM(ref_cnt) AS ref_count
                FROM (
                    SELECT TRIM(u9_supplier_code) AS supplier_code,
                           MAX(TRIM(u9_supplier_name)) AS supplier_name,
                           COUNT(*) AS ref_cnt
                    FROM material_item
                    WHERE u9_supplier_code IS NOT NULL AND CHAR_LENGTH(TRIM(u9_supplier_code)) > 0
                    GROUP BY TRIM(u9_supplier_code)
                    UNION ALL
                    SELECT TRIM(supplier_code) AS supplier_code,
                           MAX(TRIM(supplier_name)) AS supplier_name,
                           COUNT(DISTINCT material_id) AS ref_cnt
                    FROM material_supplier_u9
                    WHERE supplier_code IS NOT NULL AND CHAR_LENGTH(TRIM(supplier_code)) > 0
                    GROUP BY TRIM(supplier_code)
                ) s
                GROUP BY supplier_code
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + union + ") t", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String paged = "SELECT supplier_code, supplier_name, ref_count FROM (" + union
                + ") t ORDER BY supplier_code LIMIT ? OFFSET ?";
        List<MaterialSupplierRefRow> content = jdbcTemplate.query(
                paged,
                (rs, rowNum) -> new MaterialSupplierRefRow(
                        rs.getString("supplier_code"),
                        rs.getString("supplier_name"),
                        rs.getLong("ref_count")),
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, tot);
    }
}
