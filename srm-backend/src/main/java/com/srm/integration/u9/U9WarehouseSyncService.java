package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.config.SrmProperties;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class U9WarehouseSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;

    public record U9WarehouseSyncResult(int total, int created, int updated, int skipped, List<String> errors) {}

    @Transactional
    public U9WarehouseSyncResult syncFromU9() {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        if (!StringUtils.hasText(u9.getDecisionApiUrl())) {
            throw new BadRequestException("请配置 srm.u9.decision-api-url");
        }
        String reportPath = StringUtils.hasText(u9.getWarehouseReportPath())
                ? u9.getWarehouseReportPath().trim()
                : "API/cangku.cpt";

        String raw = u9DecisionClient.postDecision(u9, reportPath, buildCangkuParameters(), 1, -1);
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("仓库接口返回空内容");
        }
        List<U9CangkuRow> rows = parseCangkuRows(raw);
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            U9CangkuRow row = rows.get(i);
            int line = i + 1;
            if (row == null || !StringUtils.hasText(row.getCode()) || !StringUtils.hasText(row.getName())) {
                errors.add("第 " + line + " 行：仓库编码或名称为空");
                skipped++;
                continue;
            }
            if (!StringUtils.hasText(row.getGongchang())) {
                errors.add("第 " + line + " 行（编码 " + row.getCode() + "）：gongchang（工厂/品类）为空，无法匹配采购组织");
                skipped++;
                continue;
            }
            OrgUnit org = resolveProcurementOrg(row.getGongchang().trim());
            if (org == null) {
                errors.add("第 " + line + " 行（编码 " + row.getCode() + "）：未找到采购组织，gongchang="
                        + row.getGongchang().trim() + "（请与 org_unit.u9_org_code / code / name 对齐）");
                skipped++;
                continue;
            }
            String whCode = row.getCode().trim();
            String whName = row.getName().trim();
            try {
                Optional<Warehouse> existing = warehouseRepository.findByProcurementOrgAndCode(org, whCode);
                if (existing.isPresent()) {
                    Warehouse w = existing.get();
                    w.setName(whName);
                    w.setU9WhCode(whCode);
                    w.setU9Gongchang(trimToNull(row.getGongchang()));
                    warehouseRepository.save(w);
                    updated++;
                } else {
                    Warehouse w = new Warehouse();
                    w.setProcurementOrg(org);
                    w.setCode(whCode);
                    w.setName(whName);
                    w.setU9WhCode(whCode);
                    w.setU9Gongchang(trimToNull(row.getGongchang()));
                    warehouseRepository.save(w);
                    created++;
                }
            } catch (Exception e) {
                errors.add("第 " + line + " 行（编码 " + whCode + "）：" + e.getMessage());
                skipped++;
            }
        }
        log.info("U9 仓库同步完成 total={} created={} updated={} skipped={}", rows.size(), created, updated, skipped);
        return new U9WarehouseSyncResult(rows.size(), created, updated, skipped, errors);
    }

    private OrgUnit resolveProcurementOrg(String gongchang) {
        Optional<OrgUnit> ou = orgUnitRepository.findFirstByOrgTypeAndU9OrgCode(OrgUnitType.PROCUREMENT, gongchang);
        if (ou.isPresent()) {
            return ou.get();
        }
        ou = orgUnitRepository.findFirstByOrgTypeAndCode(OrgUnitType.PROCUREMENT, gongchang);
        if (ou.isPresent()) {
            return ou.get();
        }
        return orgUnitRepository.findFirstByOrgTypeAndName(OrgUnitType.PROCUREMENT, gongchang).orElse(null);
    }

    private static List<Map<String, Object>> buildCangkuParameters() {
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "cangku");
        p.put("type", "String");
        p.put("value", "");
        parameters.add(p);
        return parameters;
    }

    private List<U9CangkuRow> parseCangkuRows(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.startsWith("<") || trimmed.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
                throw new BadRequestException("仓库接口返回了 HTML 而非 JSON");
            }
            JsonNode root = MAPPER.readTree(trimmed);
            FineReportJson.assertSuccess(root);
            if (root.has("data") && root.get("data").isTextual()) {
                String inner = root.get("data").asText().trim();
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    return parseCangkuRows(inner);
                }
            }
            JsonNode array = FineReportJson.locateObjectRowArray(root);
            if (array == null || !array.isArray()) {
                throw new BadRequestException("仓库响应中未找到对象数组");
            }
            List<U9CangkuRow> out = new ArrayList<>();
            for (JsonNode n : array) {
                if (n != null && n.isObject()) {
                    out.add(MAPPER.convertValue(n, U9CangkuRow.class));
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析仓库 JSON 失败: " + e.getOriginalMessage());
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
