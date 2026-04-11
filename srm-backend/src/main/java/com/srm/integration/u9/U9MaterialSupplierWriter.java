package com.srm.integration.u9;

import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.MaterialSupplierU9;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.MaterialSupplierU9Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * lpgys 结果落库：独立事务，单行物料失败不影响其它物料。
 */
@Service
@RequiredArgsConstructor
public class U9MaterialSupplierWriter {

    private final MaterialItemRepository materialItemRepository;
    private final MaterialSupplierU9Repository materialSupplierU9Repository;

    public record Outcome(int linksSaved) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome replaceSuppliersForMaterial(Long materialId, List<U9LpgysSupplierRow> rows) {
        MaterialItem m = materialItemRepository.findById(materialId).orElseThrow();
        materialSupplierU9Repository.deleteByMaterial_Id(materialId);
        if (rows == null || rows.isEmpty()) {
            m.setU9SupplierCode(null);
            m.setU9SupplierName(null);
            materialItemRepository.save(m);
            return new Outcome(0);
        }
        List<MaterialSupplierU9> batch = new ArrayList<>();
        for (U9LpgysSupplierRow r : rows) {
            if (r == null || !StringUtils.hasText(r.getSupplierCode())) {
                continue;
            }
            MaterialSupplierU9 e = new MaterialSupplierU9();
            e.setMaterial(m);
            e.setU9SupplierCode(r.getSupplierCode().trim());
            e.setU9SupplierName(trimToNull(r.getSupplierName()));
            batch.add(e);
        }
        if (!batch.isEmpty()) {
            materialSupplierU9Repository.saveAll(batch);
        }
        U9LpgysSupplierRow first = rows.stream()
                .filter(x -> x != null && StringUtils.hasText(x.getSupplierCode()))
                .findFirst()
                .orElse(null);
        if (first != null) {
            m.setU9SupplierCode(first.getSupplierCode().trim());
            m.setU9SupplierName(trimToNull(first.getSupplierName()));
        } else {
            m.setU9SupplierCode(null);
            m.setU9SupplierName(null);
        }
        materialItemRepository.save(m);
        return new Outcome(batch.size());
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
