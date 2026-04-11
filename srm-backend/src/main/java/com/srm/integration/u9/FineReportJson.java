package com.srm.integration.u9;

import com.fasterxml.jackson.databind.JsonNode;
import com.srm.web.error.BadRequestException;

import java.util.List;

/**
 * 帆软 Decision 响应公共结构（err_code、data 数组位置）。
 */
public final class FineReportJson {

    private FineReportJson() {}

    public static void assertSuccess(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode errCode = root.get("err_code");
        if (errCode == null || errCode.isNull()) {
            errCode = root.get("errorCode");
        }
        if (errCode == null || errCode.isNull()) {
            return;
        }
        boolean ok = false;
        if (errCode.isNumber()) {
            ok = errCode.asDouble() == 0.0;
        } else if (errCode.isTextual()) {
            String t = errCode.asText().trim();
            ok = "0".equals(t) || "200".equals(t);
        }
        if (ok) {
            return;
        }
        String msg = "";
        if (root.has("err_msg") && !root.get("err_msg").isNull()) {
            msg = root.get("err_msg").asText();
        } else if (root.has("errorMsg") && !root.get("errorMsg").isNull()) {
            msg = root.get("errorMsg").asText();
        } else if (root.has("message") && !root.get("message").isNull()) {
            msg = root.get("message").asText();
        }
        throw new BadRequestException("帆软接口返回失败 err_code=" + errCode + (msg.isBlank() ? "" : "：" + msg));
    }

    /** 定位「对象数组」行列表（data 数组、data.rows、rows 等）。 */
    public static JsonNode locateObjectRowArray(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            return data;
        }
        if (data != null && data.isObject()) {
            JsonNode rows = data.get("rows");
            if (rows != null && rows.isArray()) {
                return rows;
            }
        }
        for (String key : List.of("rows", "items", "records", "Data", "Rows")) {
            if (root.has(key) && root.get(key).isArray()) {
                return root.get(key);
            }
        }
        return null;
    }
}
