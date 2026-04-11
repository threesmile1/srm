package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.web.error.BadRequestException;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析帆软 cangku.cpt（及同类）Decision JSON 为 {@link U9CangkuRow} 列表。
 */
public final class CangkuReportParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CangkuReportParser() {}

    public static List<U9CangkuRow> parseCangkuRows(String json) {
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
}
