package com.srm.integration.u9;

import com.srm.master.domain.MaterialItem;
import com.srm.master.repo.MaterialItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 单行落库：独立事务，避免在同一事务内 catch 持久化异常导致整批被标为 rollback-only。
 */
@Service
@RequiredArgsConstructor
public class U9MaterialRowWriter {

    private final MaterialItemRepository materialItemRepository;

    public enum Outcome {
        CREATED, UPDATED
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome upsert(String code, String name, String uom, U9MaterialSyncRow row) {
        Optional<MaterialItem> existing = materialItemRepository.findByCode(code);
        if (existing.isPresent()) {
            MaterialItem m = existing.get();
            fillFromRow(m, name, uom, row);
            materialItemRepository.save(m);
            return Outcome.UPDATED;
        }
        MaterialItem m = new MaterialItem();
        m.setCode(code);
        fillFromRow(m, name, uom, row);
        materialItemRepository.save(m);
        return Outcome.CREATED;
    }

    private void fillFromRow(MaterialItem m, String name, String uom, U9MaterialSyncRow row) {
        m.setName(name);
        m.setUom(uom);
        // 与物料编码一致，便于帆软等按单参数 code 与本地主数据对齐
        if (StringUtils.hasText(m.getCode())) {
            m.setU9ItemCode(m.getCode().trim());
        }
        m.setSpecification(trimToNull(row.getSpecification()));
        m.setPurchaseUnitPrice(row.getPurchaseUnitPrice());
        // wuliao 常不带供应商列：勿用 null 覆盖已有快照（含 lpgys 多供写入后的首供）
        if (StringUtils.hasText(row.getSupplierCode())) {
            m.setU9SupplierCode(row.getSupplierCode().trim());
        }
        if (StringUtils.hasText(row.getSupplierName())) {
            m.setU9SupplierName(trimToNull(row.getSupplierName()));
        }
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
