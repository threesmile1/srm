package com.srm.master.service;

import com.srm.integration.u9.CangkuWarehouseNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从物料主数据聚合「仓库名 / 供应商」引用，替代独立 U9 仓库、lpgys 同步展示。
 */
@Service
@RequiredArgsConstructor
public class MaterialDerivedMasterService {

    private final JdbcTemplate jdbcTemplate;
    private final CangkuWarehouseNameResolver cangkuWarehouseNameResolver;

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

    /** 含帆软 HTTP，不用只读长事务，避免占用连接。 */
    public Page<MaterialWarehouseRefRow> pageWarehouseRefs(Pageable pageable) {
        String union = """
                SELECT '苏州工厂' AS procurement_org, TRIM(u9_warehouse_suzhou) AS warehouse_code, COUNT(*) AS material_count
                FROM material_item
                WHERE u9_warehouse_suzhou IS NOT NULL AND CHAR_LENGTH(TRIM(u9_warehouse_suzhou)) > 0
                GROUP BY TRIM(u9_warehouse_suzhou)
                UNION ALL
                SELECT '成都工厂' AS procurement_org, TRIM(u9_warehouse_chengdu) AS warehouse_code, COUNT(*) AS material_count
                FROM material_item
                WHERE u9_warehouse_chengdu IS NOT NULL AND CHAR_LENGTH(TRIM(u9_warehouse_chengdu)) > 0
                GROUP BY TRIM(u9_warehouse_chengdu)
                UNION ALL
                SELECT '华南工厂' AS procurement_org, TRIM(u9_warehouse_huanan) AS warehouse_code, COUNT(*) AS material_count
                FROM material_item
                WHERE u9_warehouse_huanan IS NOT NULL AND CHAR_LENGTH(TRIM(u9_warehouse_huanan)) > 0
                GROUP BY TRIM(u9_warehouse_huanan)
                UNION ALL
                SELECT '水漆工厂' AS procurement_org, TRIM(u9_warehouse_shuiqi) AS warehouse_code, COUNT(*) AS material_count
                FROM material_item
                WHERE u9_warehouse_shuiqi IS NOT NULL AND CHAR_LENGTH(TRIM(u9_warehouse_shuiqi)) > 0
                GROUP BY TRIM(u9_warehouse_shuiqi)
                """;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + union + ") t", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String paged = "SELECT procurement_org, warehouse_code, material_count FROM (" + union
                + ") t ORDER BY procurement_org, warehouse_code LIMIT ? OFFSET ?";
        Map<String, String> nameByCode = new HashMap<>();
        List<MaterialWarehouseRefRow> content = jdbcTemplate.query(
                paged,
                (rs, rowNum) -> {
                    String org = rs.getString("procurement_org");
                    String code = rs.getString("warehouse_code");
                    long cnt = rs.getLong("material_count");
                    String displayName = nameByCode.computeIfAbsent(
                            code, c -> cangkuWarehouseNameResolver.resolveDisplayNameByWarehouseCode(c));
                    return new MaterialWarehouseRefRow(org, code, displayName, cnt);
                },
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
