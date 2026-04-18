package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.srm.web.error.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 将帆软 Decision 响应解析为「对象行」列表（与采购订单/物料同步逻辑一致，便于复用）。
 */
public final class FineReportObjectRowParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FineReportObjectRowParser() {}

    public static List<JsonNode> parseObjectRows(String json) {
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
                    return parseObjectRows(inner);
                }
            }
            JsonNode array = FineReportJson.locateObjectRowArray(root);
            if (array != null && array.isArray()) {
                List<JsonNode> out = new ArrayList<>();
                for (JsonNode n : array) {
                    if (n != null && n.isObject()) {
                        out.add(n);
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
            JsonNode data = root.get("data");
            if (data != null && data.isObject()) {
                JsonNode rows = data.get("rows");
                if (rows != null && rows.isArray() && !rows.isEmpty()) {
                    if (rows.get(0).isObject()) {
                        List<JsonNode> out = new ArrayList<>();
                        for (JsonNode n : rows) {
                            if (n.isObject()) {
                                out.add(n);
                            }
                        }
                        return out;
                    }
                    if (rows.get(0).isArray()) {
                        List<JsonNode> grid = tryConvertColumnsRowsGrid(data.get("columns"), rows);
                        if (!grid.isEmpty()) {
                            return grid;
                        }
                    }
                }
            }
            throw new BadRequestException("帆软响应中未找到对象行数组（也不支持解析 columns+rows 网格）");
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析帆软 JSON 失败: " + e.getOriginalMessage());
        }
    }

    private static List<JsonNode> tryConvertColumnsRowsGrid(JsonNode columnsNode, JsonNode rowsNode) {
        if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty() || !rowsNode.get(0).isArray()) {
            return List.of();
        }
        List<String> colNames = new ArrayList<>();
        int dataStart = 0;
        if (columnsNode != null && columnsNode.isArray() && !columnsNode.isEmpty()) {
            columnsNode.forEach(c -> colNames.add(textValue(c).trim()));
        } else {
            JsonNode header = rowsNode.get(0);
            for (JsonNode h : header) {
                colNames.add(textValue(h).trim());
            }
            dataStart = 1;
        }
        List<JsonNode> out = new ArrayList<>();
        for (int r = dataStart; r < rowsNode.size(); r++) {
            JsonNode row = rowsNode.get(r);
            if (!row.isArray()) {
                continue;
            }
            ObjectNode obj = MAPPER.createObjectNode();
            for (int i = 0; i < colNames.size() && i < row.size(); i++) {
                String cn = colNames.get(i);
                if (!StringUtils.hasText(cn)) {
                    continue;
                }
                JsonNode cell = row.get(i);
                if (cell == null || cell.isNull()) {
                    continue;
                }
                if (cell.isNumber()) {
                    if (cell.isIntegralNumber()) {
                        obj.put(cn, cell.longValue());
                    } else {
                        obj.put(cn, cell.decimalValue());
                    }
                } else if (cell.isTextual()) {
                    obj.put(cn, cell.asText());
                } else if (cell.isBoolean()) {
                    obj.put(cn, cell.booleanValue());
                } else {
                    obj.set(cn, cell);
                }
            }
            out.add(obj);
        }
        return out;
    }

    private static String textValue(JsonNode v) {
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isNumber()) {
            return v.decimalValue().toPlainString();
        }
        if (v.isBoolean()) {
            return v.asBoolean() ? "true" : "false";
        }
        return v.asText("");
    }
}
