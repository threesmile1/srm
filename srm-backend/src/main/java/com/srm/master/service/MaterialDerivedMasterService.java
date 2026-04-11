package com.srm.master.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 从物料主数据聚合「仓库名 / 供应商」引用，替代独立 U9 仓库、lpgys 同步展示。
 */
@Service
@RequiredArgsConstructor
public class MaterialDerivedMasterService {

    private final JdbcTemplate jdbcTemplate;

    /** 与 {@link WarehouseMasterFromMaterialService} 中采购组织命名一致；仅用于拆分 JOIN，避免跨厂 OR + 全表聚合后再分页 */
    private static final Map<String, String> PROCUREMENT_ORG_TO_MATERIAL_WH_COL = Map.of(
            "苏州工厂", "u9_warehouse_suzhou",
            "成都工厂", "u9_warehouse_chengdu",
            "华南工厂", "u9_warehouse_huanan",
            "水漆工厂", "u9_warehouse_shuiqi");

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
     * 全部「物料中出现的供应商」维度（无分页）；supplier 主档由 U9/lpgys 同步路径 {@link MasterDataService#upsertSupplierMasterForU9} 维护，勿在 GET /suppliers 全量调用本方法。
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
     * <p>
     * 性能：先对仓库主档 LIMIT/OFFSET，再仅对本页仓库 id 按采购组织拆分统计，避免「全仓库聚合后再分页」。
     */
    @Transactional(readOnly = true)
    public Page<MaterialWarehouseRefRow> pageWarehouseRefs(Pageable pageable) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse", Long.class);
        long tot = total != null ? total : 0;
        if (tot == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String pageWarehouses = """
                SELECT w.id AS wid, ou.name AS procurement_org, ou.code AS org_sort,
                       w.code AS warehouse_code, w.name AS warehouse_name
                FROM warehouse w
                JOIN org_unit ou ON ou.id = w.procurement_org_id
                ORDER BY ou.code ASC, w.code ASC
                LIMIT ? OFFSET ?
                """;
        List<WarehousePageRow> pageRows = jdbcTemplate.query(
                pageWarehouses,
                (rs, rowNum) -> new WarehousePageRow(
                        rs.getLong("wid"),
                        rs.getString("procurement_org"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name")),
                pageable.getPageSize(),
                pageable.getOffset());
        if (pageRows.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, tot);
        }
        Map<Long, Long> materialCountByWarehouseId = materialCountsForWarehouseIds(pageRows);
        List<MaterialWarehouseRefRow> content = pageRows.stream()
                .map(r -> new MaterialWarehouseRefRow(
                        r.procurementOrg(),
                        r.warehouseCode(),
                        r.warehouseName(),
                        materialCountByWarehouseId.getOrDefault(r.warehouseId(), 0L)))
                .toList();
        return new PageImpl<>(content, pageable, tot);
    }

    private record WarehousePageRow(long warehouseId, String procurementOrg, String warehouseCode, String warehouseName) {}

    /**
     * 按采购组织拆成单厂列 JOIN，避免四路 OR；仅处理本页出现的仓库 id。
     */
    private Map<Long, Long> materialCountsForWarehouseIds(List<WarehousePageRow> pageRows) {
        Map<Long, Long> counts = new HashMap<>();
        Map<String, List<WarehousePageRow>> byOrg =
                pageRows.stream().collect(Collectors.groupingBy(WarehousePageRow::procurementOrg));
        for (Map.Entry<String, List<WarehousePageRow>> e : byOrg.entrySet()) {
            String orgName = e.getKey();
            String miCol = PROCUREMENT_ORG_TO_MATERIAL_WH_COL.get(orgName);
            if (miCol == null) {
                continue;
            }
            List<Long> ids = e.getValue().stream().map(WarehousePageRow::warehouseId).toList();
            addMaterialCountsForOrg(counts, orgName, miCol, ids);
        }
        return counts;
    }

    private void addMaterialCountsForOrg(
            Map<Long, Long> counts, String orgName, String materialWhColumn, List<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return;
        }
        String inList = warehouseIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT w.id AS wid, COUNT(DISTINCT mi.id) AS material_count
                FROM warehouse w
                JOIN org_unit ou ON ou.id = w.procurement_org_id AND ou.name = ?
                LEFT JOIN material_item mi ON mi.%s IS NOT NULL AND TRIM(mi.%s) = w.code
                WHERE w.id IN (%s)
                GROUP BY w.id
                """
                .formatted(materialWhColumn, materialWhColumn, inList);
        List<Object> args = new ArrayList<>();
        args.add(orgName);
        args.addAll(warehouseIds);
        jdbcTemplate.query(
                sql,
                rs -> {
                    counts.put(rs.getLong("wid"), rs.getLong("material_count"));
                },
                args.toArray());
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
