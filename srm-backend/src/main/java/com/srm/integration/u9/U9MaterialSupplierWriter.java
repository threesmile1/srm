package com.srm.integration.u9;

import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.MaterialSupplierU9;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.MaterialSupplierU9Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * lpgys 结果落库：独立事务，单行物料失败不影响其它物料。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class U9MaterialSupplierWriter {

    private final MaterialItemRepository materialItemRepository;
    private final MaterialSupplierU9Repository materialSupplierU9Repository;

    public record Outcome(int linksSaved) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome replaceSuppliersForMaterial(Long materialId, List<U9LpgysSupplierRow> rows) {
        // 无有效供应商行时不落库、不 delete，避免报表空结果/列名不符把已有关系清空
        if (rows == null || rows.isEmpty()) {
            return new Outcome(0);
        }
        List<U9LpgysSupplierRow> valid = rows.stream()
                .filter(r -> r != null && StringUtils.hasText(normalizeU9SupplierCode(r.getSupplierCode())))
                .collect(Collectors.toList());
        if (valid.isEmpty()) {
            if (!rows.isEmpty()) {
                log.warn("lpgys materialId={} 返回 {} 行但供应商编码均为空（无首供或列名未映射），跳过写入", materialId,
                        rows.size());
            }
            return new Outcome(0);
        }
        // 帆软可能返回同一供应商编码多行，唯一键 (material_id, u9_supplier_code) 需去重，顺序保留首行作首供
        List<U9LpgysSupplierRow> deduped = dedupeBySupplierCode(valid);
        if (deduped.size() < valid.size()) {
            log.info("lpgys materialId={} 供应商行去重：{} -> {}（重复 u9_supplier_code）", materialId, valid.size(),
                    deduped.size());
        }
        MaterialItem m = materialItemRepository.findById(materialId).orElseThrow();
        materialSupplierU9Repository.deleteByMaterial_Id(materialId);
        materialSupplierU9Repository.flush();
        List<MaterialSupplierU9> batch = new ArrayList<>();
        for (U9LpgysSupplierRow r : deduped) {
            MaterialSupplierU9 e = new MaterialSupplierU9();
            e.setMaterial(m);
            e.setU9SupplierCode(normalizeU9SupplierCode(r.getSupplierCode()));
            e.setU9SupplierName(trimToNull(r.getSupplierName()));
            batch.add(e);
        }
        materialSupplierU9Repository.saveAll(batch);
        U9LpgysSupplierRow first = deduped.get(0);
        m.setU9SupplierCode(normalizeU9SupplierCode(first.getSupplierCode()));
        m.setU9SupplierName(trimToNull(first.getSupplierName()));
        materialItemRepository.save(m);
        return new Outcome(batch.size());
    }

    /** 按规范化后的供应商编码去重，保留首次出现顺序 */
    private static List<U9LpgysSupplierRow> dedupeBySupplierCode(List<U9LpgysSupplierRow> valid) {
        List<U9LpgysSupplierRow> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (U9LpgysSupplierRow r : valid) {
            String key = normalizeU9SupplierCode(r.getSupplierCode());
            if (seen.add(key)) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * 与入库一致：strip、去 BOM/零宽字符，避免「肉眼相同」的两串在去重集合里算两个、库里唯一键算一个。
     */
    static String normalizeU9SupplierCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.strip();
        s = s.replace("\uFEFF", "").replace("\u200B", "").replace("\u200C", "").replace("\u200D", "");
        return s.trim();
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
