package com.srm.master.service;

import com.srm.config.SrmProperties;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.integration.u9.CangkuWarehouseNameResolver;
import com.srm.master.domain.MaterialItem;
import com.srm.master.repo.MaterialItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 将物料表上的四厂默认仓（编码）同步到 {@code warehouse} 主档，供 PO/收货等引用；
 * 名称优先由 {@link CangkuWarehouseNameResolver} 按编码调帆软 cangku.cpt。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WarehouseMasterFromMaterialService {

    private final SrmProperties properties;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final CangkuWarehouseNameResolver cangkuWarehouseNameResolver;
    private final MaterialItemRepository materialItemRepository;

    /**
     * 根据一条物料当前四厂仓字段，在对应采购组织下 upsert {@link Warehouse}。
     * 独立事务，避免与外层同步任务互相回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWarehousesFromMaterialItem(MaterialItem m) {
        applyWarehousesFromMaterial(m, new HashMap<>());
    }

    /**
     * 全量扫描本地物料，重建 warehouse（单事务；首次启用或补历史数据时可用，较慢）。
     */
    @Transactional
    public void rebuildWarehousesFromAllMaterials() {
        Map<String, String> nameCache = new HashMap<>();
        for (MaterialItem m : materialItemRepository.findAll()) {
            applyWarehousesFromMaterial(m, nameCache);
        }
    }

    private void applyWarehousesFromMaterial(MaterialItem m, Map<String, String> nameCache) {
        if (m == null) {
            return;
        }
        upsertOne(m.getU9WarehouseSuzhou(), "suzhou", "苏州工厂", nameCache);
        upsertOne(m.getU9WarehouseChengdu(), "chengdu", "成都工厂", nameCache);
        upsertOne(m.getU9WarehouseHuanan(), "huanan", "华南工厂", nameCache);
        upsertOne(m.getU9WarehouseShuiqi(), "shuiqi", "水漆工厂", nameCache);
    }

    private void upsertOne(
            String warehouseCodeRaw,
            String factoryKey,
            String gongchangLabel,
            Map<String, String> nameCache
    ) {
        if (!StringUtils.hasText(warehouseCodeRaw)) {
            return;
        }
        String code = warehouseCodeRaw.trim();
        OrgUnit org = resolveProcurementOrgForFactory(factoryKey);
        if (org == null) {
            log.warn("未解析到采购组织，跳过写入 warehouse：工厂维度={}，请配置 srm.u9.factory-warehouse-org-code-{} 或在 org_unit 中建名称为「{}」的采购组织",
                    factoryKey, factoryKey, labelForFactory(factoryKey));
            return;
        }
        String displayName = nameCache.computeIfAbsent(
                code, c -> cangkuWarehouseNameResolver.resolveDisplayNameByWarehouseCode(c));
        if (!StringUtils.hasText(displayName)) {
            displayName = code;
        } else {
            displayName = displayName.trim();
        }
        Optional<Warehouse> existing = warehouseRepository.findByProcurementOrgAndCode(org, code);
        if (existing.isPresent()) {
            Warehouse w = existing.get();
            if (!displayName.equals(w.getName())) {
                w.setName(displayName);
            }
            w.setU9WhCode(code);
            w.setU9Gongchang(gongchangLabel);
            warehouseRepository.save(w);
        } else {
            Warehouse w = new Warehouse();
            w.setProcurementOrg(org);
            w.setCode(code);
            w.setName(displayName);
            w.setU9WhCode(code);
            w.setU9Gongchang(gongchangLabel);
            warehouseRepository.save(w);
        }
    }

    private static String labelForFactory(String factoryKey) {
        return switch (factoryKey) {
            case "suzhou" -> "苏州工厂";
            case "chengdu" -> "成都工厂";
            case "huanan" -> "华南工厂";
            case "shuiqi" -> "水漆工厂";
            default -> factoryKey;
        };
    }

    private OrgUnit resolveProcurementOrgForFactory(String factoryKey) {
        SrmProperties.U9 u9 = properties.getU9();
        String orgCode = switch (factoryKey) {
            case "suzhou" -> nullToEmpty(u9.getFactoryWarehouseOrgCodeSuzhou());
            case "chengdu" -> nullToEmpty(u9.getFactoryWarehouseOrgCodeChengdu());
            case "huanan" -> nullToEmpty(u9.getFactoryWarehouseOrgCodeHuanan());
            case "shuiqi" -> nullToEmpty(u9.getFactoryWarehouseOrgCodeShuiqi());
            default -> "";
        };
        if (StringUtils.hasText(orgCode)) {
            Optional<OrgUnit> ou = orgUnitRepository.findFirstByOrgTypeAndCode(OrgUnitType.PROCUREMENT, orgCode.trim());
            if (ou.isPresent()) {
                return ou.get();
            }
            log.debug("factory-warehouse-org-code 未匹配采购组织 code={}", orgCode);
        }
        String name = labelForFactory(factoryKey);
        return orgUnitRepository.findFirstByOrgTypeAndName(OrgUnitType.PROCUREMENT, name).orElse(null);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
