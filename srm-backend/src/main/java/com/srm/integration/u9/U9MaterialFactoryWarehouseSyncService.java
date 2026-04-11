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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从帆软 cangku_yigui / cangku_shuiqi 同步物料在各工厂的默认存储仓库。
 * <p>按物料编码逐次请求（parameters 中传入 code），避免全量一次拉取超时。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class U9MaterialFactoryWarehouseSyncService {

    private static final int PROGRESS_LOG_EVERY = 500;

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

    /**
     * 全量：本地每个物料编码各请求两次帆软（衣柜 + 水漆）。
     *
     * @param requestedCodes 非空则只同步这些编码；{@code null} 或空列表表示全部物料
     */
    public FactoryWarehouseSyncResult syncFromU9(List<String> requestedCodes) {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        if (!StringUtils.hasText(u9.getDecisionApiUrl())) {
            throw new BadRequestException("请配置 srm.u9.decision-api-url");
        }
        try {
            return syncFromU9AfterChecks(u9, requestedCodes);
        } catch (BadRequestException e) {
            log.warn("四厂仓库同步失败（帆软请求或 JSON 解析）: {}", e.getMessage());
            throw e;
        }
    }

    private FactoryWarehouseSyncResult syncFromU9AfterChecks(SrmProperties.U9 u9, List<String> requestedCodes) {
        List<String> codes = resolveTargetCodes(requestedCodes);
        if (codes.isEmpty()) {
            return new FactoryWarehouseSyncResult(0, 0, 0, 0, 0, 0, List.of());
        }

        String yiguiPath = StringUtils.hasText(u9.getMaterialYiguiReportPath())
                ? u9.getMaterialYiguiReportPath().trim()
                : "API/cangku_yigui.cpt";
        String shuiqiPath = StringUtils.hasText(u9.getMaterialShuiqiReportPath())
                ? u9.getMaterialShuiqiReportPath().trim()
                : "API/cangku_shuiqi.cpt";

        List<String> errors = new ArrayList<>();
        int yiguiCalls = 0;
        int yu = 0;
        int ys = 0;
        int shuiqiCalls = 0;
        int su = 0;
        int ss = 0;

        int pageSize = u9.getPageSize();
        int pageNumber = u9.getPageNumber();

        for (int idx = 0; idx < codes.size(); idx++) {
            String code = codes.get(idx);
            if (idx > 0 && idx % PROGRESS_LOG_EVERY == 0) {
                log.info("四厂仓库同步进度 {}/{} 料号={}", idx, codes.size(), code);
            }

            Optional<MaterialItem> itemOpt = resolveByReportLiaohao(code);
            if (itemOpt.isEmpty()) {
                errors.add("料号 " + code + "：本地不存在（编码/U9 料号均未匹配），已跳过");
                ys++;
                ss++;
                continue;
            }
            MaterialItem material = itemOpt.get();

            try {
                List<Map<String, Object>> yiguiParams = parametersForMaterialCode(u9, code);
                String rawYigui = u9DecisionClient.postDecision(u9, yiguiPath, yiguiParams, pageNumber, pageSize);
                yiguiCalls++;
                List<U9MaterialYiguiRow> yiguiRows = parseYiguiRows(rawYigui);
                U9MaterialYiguiRow yRow = yiguiRows.isEmpty() ? null : yiguiRows.get(0);
                if (yRow != null && hasAnyYiguiWarehouse(yRow)) {
                    material.setU9WarehouseSuzhou(trimToNull(yRow.getCangkuSuzhou()));
                    material.setU9WarehouseChengdu(trimToNull(yRow.getCangkuChengdu()));
                    material.setU9WarehouseHuanan(trimToNull(yRow.getCangkuHuanan()));
                    yu++;
                } else if (yRow != null) {
                    ys++;
                    errors.add("料号 " + code + "：衣柜报表有行但苏州/成都/华南仓字段未解析到值（请核对帆软列名与 JSON）");
                } else {
                    ys++;
                }
            } catch (BadRequestException e) {
                ys++;
                errors.add("料号 " + code + " 衣柜报表：" + e.getMessage());
            }

            try {
                List<Map<String, Object>> shuiqiParams = parametersForMaterialCode(u9, code);
                String rawShuiqi = u9DecisionClient.postDecision(u9, shuiqiPath, shuiqiParams, pageNumber, pageSize);
                shuiqiCalls++;
                List<U9MaterialShuiqiRow> shuiqiRows = parseShuiqiRows(rawShuiqi);
                U9MaterialShuiqiRow sRow = shuiqiRows.isEmpty() ? null : shuiqiRows.get(0);
                if (sRow != null && StringUtils.hasText(sRow.getCangkuShuiqi())) {
                    material.setU9WarehouseShuiqi(trimToNull(sRow.getCangkuShuiqi()));
                    su++;
                } else if (sRow != null) {
                    ss++;
                    errors.add("料号 " + code + "：水漆报表有行但水漆仓字段未解析到值（请核对帆软列名与 JSON）");
                } else {
                    ss++;
                }
            } catch (BadRequestException e) {
                ss++;
                errors.add("料号 " + code + " 水漆报表：" + e.getMessage());
            }

            materialItemRepository.save(material);
        }

        log.info("U9 物料四厂仓库同步(按料号) materials={} yigui ok={} skip/err={} shuiqi ok={} skip/err={}",
                codes.size(), yu, ys, su, ss);
        return new FactoryWarehouseSyncResult(
                yiguiCalls, yu, ys,
                shuiqiCalls, su, ss,
                capErrors(errors, 200));
    }

    private List<String> resolveTargetCodes(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return materialItemRepository.findAllCodesOrderByCode();
        }
        List<String> out = new ArrayList<>();
        for (String c : requestedCodes) {
            String k = normalizeReportMaterialKey(c);
            if (StringUtils.hasText(k) && !out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * 与帆软模板一致：默认单参数 {@code name=code}（可配置），{@code value}=物料编码。
     */
    private static List<Map<String, Object>> parametersForMaterialCode(SrmProperties.U9 u9, String materialCode) {
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
                String v = fp.getValue();
                if (!StringUtils.hasText(v)) {
                    v = materialCode;
                }
                p.put("value", v != null ? v : "");
                out.add(p);
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        String paramName = StringUtils.hasText(u9.getFactoryWarehouseReportParameterName())
                ? u9.getFactoryWarehouseReportParameterName().trim()
                : "code";
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", paramName);
        p.put("type", "String");
        p.put("value", materialCode);
        return List.of(p);
    }

    /**
     * 报表料号可能与本地 {@link MaterialItem#getCode()} 或 {@link MaterialItem#getU9ItemCode()} 一致（大小写、空白差异已放宽）。
     */
    private Optional<MaterialItem> resolveByReportLiaohao(String liaohao) {
        Optional<MaterialItem> byCode = materialItemRepository.findByCode(liaohao);
        if (byCode.isPresent()) {
            return byCode;
        }
        byCode = materialItemRepository.findByCodeIgnoreCase(liaohao);
        if (byCode.isPresent()) {
            return byCode;
        }
        Optional<MaterialItem> byU9 = materialItemRepository.findFirstByU9ItemCode(liaohao);
        if (byU9.isPresent()) {
            return byU9;
        }
        return materialItemRepository.findFirstByU9ItemCodeIgnoreCase(liaohao);
    }

    private static List<String> capErrors(List<String> errors, int max) {
        if (errors.size() <= max) {
            return errors;
        }
        List<String> head = new ArrayList<>(errors.subList(0, max));
        head.add("…（其余 " + (errors.size() - max) + " 条提示已省略，请修正匹配规则或缩小报表范围后重试）");
        return head;
    }

    /** 与帆软/Excel 常见差异：首尾空白、BOM、零宽字符、全角空格。 */
    private static String normalizeReportMaterialKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1).trim();
        }
        s = s.replace("\u200B", "").replace("\uFEFF", "");
        s = s.replace('\u3000', ' ').trim();
        return s;
    }

    private List<U9MaterialYiguiRow> parseYiguiRows(String json) {
        try {
            JsonNode array = extractObjectRowArray(json);
            List<U9MaterialYiguiRow> out = new ArrayList<>();
            for (JsonNode n : array) {
                if (n != null && n.isObject()) {
                    U9MaterialYiguiRow r = MAPPER.convertValue(n, U9MaterialYiguiRow.class);
                    enrichYiguiRowFromNode(n, r);
                    out.add(r);
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析 JSON 失败: " + e.getOriginalMessage());
        }
    }

    private List<U9MaterialShuiqiRow> parseShuiqiRows(String json) {
        try {
            JsonNode array = extractObjectRowArray(json);
            List<U9MaterialShuiqiRow> out = new ArrayList<>();
            for (JsonNode n : array) {
                if (n != null && n.isObject()) {
                    U9MaterialShuiqiRow r = MAPPER.convertValue(n, U9MaterialShuiqiRow.class);
                    enrichShuiqiRowFromNode(n, r);
                    out.add(r);
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析 JSON 失败: " + e.getOriginalMessage());
        }
    }

    private static boolean hasAnyYiguiWarehouse(U9MaterialYiguiRow r) {
        return StringUtils.hasText(r.getCangkuSuzhou())
                || StringUtils.hasText(r.getCangkuChengdu())
                || StringUtils.hasText(r.getCangkuHuanan());
    }

    /** 帆软列名多样：在标准反序列化后再按常见别名与列名模糊匹配补全 */
    private static void enrichYiguiRowFromNode(JsonNode n, U9MaterialYiguiRow r) {
        if (!StringUtils.hasText(r.getCangkuSuzhou())) {
            r.setCangkuSuzhou(firstNonBlankByKeys(n,
                    "cangku_suzhou", "苏州仓库", "苏州仓", "仓库苏州", "仓库_苏州", "CK_SU", "ck_suzhou"));
        }
        if (!StringUtils.hasText(r.getCangkuChengdu())) {
            r.setCangkuChengdu(firstNonBlankByKeys(n,
                    "cangku_chengdu", "成都仓库", "成都仓", "仓库成都", "仓库_成都", "CK_CD", "ck_chengdu"));
        }
        if (!StringUtils.hasText(r.getCangkuHuanan())) {
            r.setCangkuHuanan(firstNonBlankByKeys(n,
                    "cangku_huanan", "华南仓库", "华南仓", "仓库华南", "仓库_华南", "CK_HN", "ck_huanan"));
        }
        if (!StringUtils.hasText(r.getCangkuSuzhou())) {
            r.setCangkuSuzhou(firstTextByKeyContains(n, "suzhou", "苏州"));
        }
        if (!StringUtils.hasText(r.getCangkuChengdu())) {
            r.setCangkuChengdu(firstTextByKeyContains(n, "chengdu", "成都"));
        }
        if (!StringUtils.hasText(r.getCangkuHuanan())) {
            r.setCangkuHuanan(firstTextByKeyContains(n, "huanan", "华南"));
        }
    }

    private static void enrichShuiqiRowFromNode(JsonNode n, U9MaterialShuiqiRow r) {
        if (!StringUtils.hasText(r.getCangkuShuiqi())) {
            r.setCangkuShuiqi(firstNonBlankByKeys(n,
                    "cangku_shuiqi", "水漆仓库", "水漆仓", "仓库水漆", "CK_SQ", "ck_shuiqi"));
        }
        if (!StringUtils.hasText(r.getCangkuShuiqi())) {
            r.setCangkuShuiqi(firstTextByKeyContains(n, "shuiqi", "水漆"));
        }
    }

    private static String firstNonBlankByKeys(JsonNode n, String... keys) {
        for (String k : keys) {
            String t = textAt(n, k);
            if (StringUtils.hasText(t)) {
                return t.trim();
            }
        }
        return null;
    }

    private static String textAt(JsonNode n, String key) {
        if (n == null || !n.has(key) || n.get(key).isNull()) {
            return null;
        }
        JsonNode v = n.get(key);
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isNumber()) {
            return v.asText();
        }
        return null;
    }

    private static String firstTextByKeyContains(JsonNode n, String... tokens) {
        Iterator<String> names = n.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            String lower = name.toLowerCase();
            for (String t : tokens) {
                if (t != null && (lower.contains(t.toLowerCase()) || name.contains(t))) {
                    String val = textAt(n, name);
                    if (StringUtils.hasText(val)) {
                        return val.trim();
                    }
                }
            }
        }
        return null;
    }

    private JsonNode extractObjectRowArray(String json) throws JsonProcessingException {
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
                return extractObjectRowArray(inner);
            }
        }
        JsonNode array = FineReportJson.locateObjectRowArray(root);
        if (array == null || !array.isArray()) {
            throw new BadRequestException("响应中未找到对象数组");
        }
        return array;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
