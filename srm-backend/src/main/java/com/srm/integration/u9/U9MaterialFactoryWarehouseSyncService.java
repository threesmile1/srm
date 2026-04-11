package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.config.SrmProperties;
import com.srm.master.domain.MaterialItem;
import com.srm.master.repo.MaterialItemRepository;
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

/**
 * 从帆软 cangku_yigui / cangku_shuiqi 同步物料在各工厂的默认存储仓库。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class U9MaterialFactoryWarehouseSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;
    private final MaterialItemRepository materialItemRepository;

    public record FactoryWarehouseSyncResult(
            int yiguiRows,
            int yiguiUpdated,
            int yiguiSkipped,
            int shuiqiRows,
            int shuiqiUpdated,
            int shuiqiSkipped,
            List<String> errors
    ) {}

    @Transactional
    public FactoryWarehouseSyncResult syncFromU9() {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        if (!StringUtils.hasText(u9.getDecisionApiUrl())) {
            throw new BadRequestException("请配置 srm.u9.decision-api-url");
        }
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> parameters = resolveFrParameters(u9);

        String yiguiPath = StringUtils.hasText(u9.getMaterialYiguiReportPath())
                ? u9.getMaterialYiguiReportPath().trim()
                : "API/cangku_yigui.cpt";
        String shuiqiPath = StringUtils.hasText(u9.getMaterialShuiqiReportPath())
                ? u9.getMaterialShuiqiReportPath().trim()
                : "API/cangku_shuiqi.cpt";

        String rawYigui = u9DecisionClient.postDecision(u9, yiguiPath, parameters, 1, -1);
        List<U9MaterialYiguiRow> yiguiRows = parseYiguiRows(rawYigui);
        int yu = 0;
        int ys = 0;
        for (int i = 0; i < yiguiRows.size(); i++) {
            U9MaterialYiguiRow row = yiguiRows.get(i);
            int line = i + 1;
            if (row == null || !StringUtils.hasText(row.getLiaohao())) {
                errors.add("衣柜报表 第 " + line + " 行：liaohao 为空");
                ys++;
                continue;
            }
            String code = row.getLiaohao().trim();
            Optional<MaterialItem> opt = materialItemRepository.findByCode(code);
            if (opt.isEmpty()) {
                errors.add("衣柜报表 第 " + line + " 行：本地无物料编码 " + code);
                ys++;
                continue;
            }
            MaterialItem m = opt.get();
            m.setU9WarehouseSuzhou(trimToNull(row.getCangkuSuzhou()));
            m.setU9WarehouseChengdu(trimToNull(row.getCangkuChengdu()));
            m.setU9WarehouseHuanan(trimToNull(row.getCangkuHuanan()));
            materialItemRepository.save(m);
            yu++;
        }

        String rawShuiqi = u9DecisionClient.postDecision(u9, shuiqiPath, parameters, 1, -1);
        List<U9MaterialShuiqiRow> shuiqiRows = parseShuiqiRows(rawShuiqi);
        int su = 0;
        int ss = 0;
        for (int i = 0; i < shuiqiRows.size(); i++) {
            U9MaterialShuiqiRow row = shuiqiRows.get(i);
            int line = i + 1;
            if (row == null || !StringUtils.hasText(row.getLiaohao())) {
                errors.add("水漆报表 第 " + line + " 行：liaohao 为空");
                ss++;
                continue;
            }
            String code = row.getLiaohao().trim();
            Optional<MaterialItem> opt = materialItemRepository.findByCode(code);
            if (opt.isEmpty()) {
                errors.add("水漆报表 第 " + line + " 行：本地无物料编码 " + code);
                ss++;
                continue;
            }
            MaterialItem m = opt.get();
            m.setU9WarehouseShuiqi(trimToNull(row.getCangkuShuiqi()));
            materialItemRepository.save(m);
            su++;
        }

        log.info("U9 物料四厂仓库同步 yigui rows={} updated={} shuiqi rows={} updated={}",
                yiguiRows.size(), yu, shuiqiRows.size(), su);
        return new FactoryWarehouseSyncResult(
                yiguiRows.size(), yu, ys,
                shuiqiRows.size(), su, ss,
                errors);
    }

    private static List<Map<String, Object>> resolveFrParameters(SrmProperties.U9 u9) {
        List<SrmProperties.FineReportParameter> cfg = u9.getMaterialFactoryWarehouseParameters();
        if (cfg != null && !cfg.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (SrmProperties.FineReportParameter fp : cfg) {
                if (fp == null) {
                    continue;
                }
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", fp.getName() != null ? fp.getName() : "");
                p.put("type", StringUtils.hasText(fp.getType()) ? fp.getType() : "String");
                p.put("value", fp.getValue() != null ? fp.getValue() : "");
                out.add(p);
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "liaohao");
        p.put("type", "String");
        p.put("value", "");
        return List.of(p);
    }

    private List<U9MaterialYiguiRow> parseYiguiRows(String json) {
        return parseObjectRows(json, U9MaterialYiguiRow.class);
    }

    private List<U9MaterialShuiqiRow> parseShuiqiRows(String json) {
        return parseObjectRows(json, U9MaterialShuiqiRow.class);
    }

    private <T> List<T> parseObjectRows(String json, Class<T> type) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.startsWith("<") || trimmed.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
                throw new BadRequestException("帆软接口返回了 HTML 而非 JSON");
            }
            JsonNode root = MAPPER.readTree(trimmed);
            FineReportJson.assertSuccess(root);
            if (root.has("data") && root.get("data").isTextual()) {
                String inner = root.get("data").asText().trim();
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    return parseObjectRows(inner, type);
                }
            }
            JsonNode array = FineReportJson.locateObjectRowArray(root);
            if (array == null || !array.isArray()) {
                throw new BadRequestException("响应中未找到对象数组");
            }
            List<T> out = new ArrayList<>();
            for (JsonNode n : array) {
                if (n != null && n.isObject()) {
                    out.add(MAPPER.convertValue(n, type));
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析 JSON 失败: " + e.getOriginalMessage());
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
