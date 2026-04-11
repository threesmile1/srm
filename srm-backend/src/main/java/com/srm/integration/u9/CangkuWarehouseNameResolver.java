package com.srm.integration.u9;

import com.srm.config.SrmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按仓库编码调用帆软 {@code cangku.cpt}（parameters: name=code, value=仓库编码），取返回行中的 {@code name} 作为仓库名称。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CangkuWarehouseNameResolver {

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;

    /**
     * @return 解析到的名称；U9 未配置、请求失败或无名称时返回 {@code null}
     */
    public String resolveDisplayNameByWarehouseCode(String warehouseCode) {
        if (!StringUtils.hasText(warehouseCode)) {
            return null;
        }
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled() || !StringUtils.hasText(u9.getDecisionApiUrl())) {
            return null;
        }
        String reportPath = StringUtils.hasText(u9.getWarehouseReportPath())
                ? u9.getWarehouseReportPath().trim()
                : "API/cangku.cpt";
        String code = warehouseCode.trim();
        try {
            String raw = u9DecisionClient.postDecision(u9, reportPath, buildParametersByCode(code), 1, -1);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            List<U9CangkuRow> rows = CangkuReportParser.parseCangkuRows(raw);
            return pickNameForCode(rows, code);
        } catch (Exception e) {
            log.debug("cangku 按编码解析名称失败 code={}: {}", code, e.getMessage());
            return null;
        }
    }

    private static List<Map<String, Object>> buildParametersByCode(String warehouseCode) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "code");
        p.put("type", "String");
        p.put("value", warehouseCode);
        parameters.add(p);
        return parameters;
    }

    static String pickNameForCode(List<U9CangkuRow> rows, String wantedCode) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (U9CangkuRow r : rows) {
            if (r == null || !StringUtils.hasText(r.getName())) {
                continue;
            }
            if (StringUtils.hasText(r.getCode()) && wantedCode.equals(r.getCode().trim())) {
                return r.getName().trim();
            }
        }
        for (U9CangkuRow r : rows) {
            if (r != null && StringUtils.hasText(r.getName())) {
                return r.getName().trim();
            }
        }
        return null;
    }
}
