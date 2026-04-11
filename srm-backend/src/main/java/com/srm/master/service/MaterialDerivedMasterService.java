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

    /** 与 {@link #pageSupplierRefs} 一致：物料快照 + material_supplier_u9 合并后的供应商维度 */
    private static final String MATERIAL_SUPPLIER_REF_UNION = """
            SELECT u9_supplier_code, MAX(u9_supplier_name) AS u9_supplier_name, SUM(ref_cnt) AS ref_count
            FROM (
                SELECT TRIM(u9_supplier_code) AS u9_supplier_code,
                       MAX(TRIM(u9_supplier_name)) AS u9_supplier_name,
                       COUNT(*) AS ref_cnt
                FROM material_item
                WHERE u9_supplier_code IS NOT NULL AND CHAR_LENGTH(TRIM(u9_supplier_code)) > 0
                GROUP BY TRIM(u9_supplier_code)
                UNION ALL
                SELECT TRIM(u9_supplier_code) AS u9_supplier_code,
                       MAX(TRIM(u9_supplier_name)) AS u9_supplier_name,
                       COUNT(DISTINCT material_id) AS ref_cnt
                FROM material_supplier_u9
                WHERE u9_supplier_code IS NOT NULL AND CHAR_LENGTH(TRIM(u9_supplier_code)) > 0
                GROUP BY TRIM(u9_supplier_code)
            ) s
            GROUP BY u9_supplier_code
            """;

    /**
     * @param procurementOrg 采购组织维度：苏州工厂 / 成都工厂 / 华南工厂 / 水漆工厂
     * @param warehouseCode  物料表中的仓库编码（原与「名称」混用列）
     * @param warehouseName  由帆软 cangku.cpt 按编码解析；失败时为 null
     */
    public record MaterialWarehouseRefRow(
            String procurementOrg,
            String warehouseCode,
            String warehouseName,
            long materialCount
    ) {}

    public record MaterialSupplierRefRow(String u9SupplierCode, String u9SupplierName, long refCount) {}

    /**
     * 全部「物料中出现的供应商」维度（无分页），供 {@link MasterDataService#listSuppliers()} 同步 supplier 主档。
     */
    @Transactional(readOnly = true)
    public List<MaterialSupplierRefRow> listAllMaterialSupplierRefs() {
        String sql = "SELECT u9_supplier_code, u9_supplier_name, ref_count FROM (" + MATERIAL_SUPPLIER_REF_UNION
                + ") t ORDER BY u9_supplier_code";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MaterialSupplierRefRow(
                        rs.getString("u9_supplier_code"),
                        rs.getString("u9_supplier_name"),
                        rs.getLong("ref_count")));
    }

    /**
     * 分页列表来自 {@code warehouse} 主档（名称等由物料四厂仓同步时写入）；「物料条数」按各厂列与采购组织名称对齐统计。
     */
    @Transactional(readOnly = true)
    public Page<MaterialWarehouseRefRow> pageWarehouseRefs(Pageable pageable) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String paged = """
                SELECT procurement_org, warehouse_code, warehouse_name, material_count FROM (
                    SELECT ou.name AS procurement_org,
                           ou.code AS org_sort,
                           w.code AS warehouse_code,
                           w.name AS warehouse_name,
                           COUNT(DISTINCT mi.id) AS material_count
                    FROM warehouse w
                    JOIN org_unit ou ON ou.id = w.procurement_org_id
                    LEFT JOIN material_item mi ON (
                        (ou.name = '苏州工厂' AND mi.u9_warehouse_suzhou IS NOT NULL
                            AND TRIM(mi.u9_warehouse_suzhou) = w.code)
                        OR (ou.name = '成都工厂' AND mi.u9_warehouse_chengdu IS NOT NULL
                            AND TRIM(mi.u9_warehouse_chengdu) = w.code)
                        OR (ou.name = '华南工厂' AND mi.u9_warehouse_huanan IS NOT NULL
                            AND TRIM(mi.u9_warehouse_huanan) = w.code)
                        OR (ou.name = '水漆工厂' AND mi.u9_warehouse_shuiqi IS NOT NULL
                            AND TRIM(mi.u9_warehouse_shuiqi) = w.code)
                    )
                    GROUP BY w.id, ou.name, ou.code, w.code, w.name
                ) t
                ORDER BY org_sort, warehouse_code
                LIMIT ? OFFSET ?
                """;
        List<MaterialWarehouseRefRow> content = jdbcTemplate.query(
                paged,
                (rs, rowNum) -> new MaterialWarehouseRefRow(
                        rs.getString("procurement_org"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"),
                        rs.getLong("material_count")),
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, tot);
    }

    @Transactional(readOnly = true)
    public Page<MaterialSupplierRefRow> pageSupplierRefs(Pageable pageable) {
        String union = MATERIAL_SUPPLIER_REF_UNION;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + union + ") t", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String paged = "SELECT u9_supplier_code, u9_supplier_name, ref_count FROM (" + union
                + ") t ORDER BY u9_supplier_code LIMIT ? OFFSET ?";
        List<MaterialSupplierRefRow> content = jdbcTemplate.query(
                paged,
                (rs, rowNum) -> new MaterialSupplierRefRow(
                        rs.getString("u9_supplier_code"),
                        rs.getString("u9_supplier_name"),
                        rs.getLong("ref_count")),
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, tot);
    }
}
