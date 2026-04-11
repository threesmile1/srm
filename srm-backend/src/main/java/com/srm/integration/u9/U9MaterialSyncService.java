package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.config.SrmProperties;
import com.srm.master.domain.MaterialItem;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class U9MaterialSyncService {

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;
    private final U9MaterialRowWriter materialRowWriter;
    private final MaterialItemRepository materialItemRepository;
    private final U9MaterialSupplierWriter materialSupplierWriter;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * @param lpgysMaterialsTried   调用 lpgys 的物料条数（每料号一次）
     * @param lpgysSupplierLinksUpserted 写入 material_supplier_u9 的行数（多供应商可大于 tried）
     */
    public record U9MaterialSyncResult(
            int total,
            int created,
            int updated,
            int skipped,
            List<String> errors,
            int lpgysMaterialsTried,
            int lpgysSupplierLinksUpserted
    ) {
        public U9MaterialSyncResult(int total, int created, int updated, int skipped, List<String> errors) {
            this(total, created, updated, skipped, errors, 0, 0);
        }
    }

    /**
     * 从配置拉取并落库（需 srm.u9.enabled=true）。
     * 帆软 Decision：当 {@code srm.u9.sync-page-size} 大于 0 时按页拉取（推荐），否则单次请求使用 {@code page_number}/{@code page_size}。
     * 取数在事务外执行；落库按行 {@link U9MaterialRowWriter} 独立事务，单行失败不影响其它行。
     */
    public U9MaterialSyncResult fetchAndApply() {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        List<U9MaterialSyncRow> rows;
        if (StringUtils.hasText(u9.getDecisionApiUrl())) {
            rows = fetchAllRowsFromFineReport(u9);
        } else {
            String url = resolveMaterialUrl(u9);
            if (!StringUtils.hasText(url)) {
                throw new BadRequestException("请配置 srm.u9.decision-api-url（帆软 POST），或 material-sync-url / base-url（GET）");
            }
            String raw = getWithOptionalAuth(u9, url);
            if (raw == null || raw.isBlank()) {
                throw new BadRequestException("物料接口返回空内容");
            }
            rows = parseMaterialRowsFromResponse(raw);
        }
        U9MaterialSyncResult materialResult = apply(rows);
        if (!shouldSyncLpgys(u9)) {
            return materialResult;
        }
        return mergeLpgysPhase(u9, materialResult);
    }

    private static boolean shouldSyncLpgys(SrmProperties.U9 u9) {
        return u9.isSyncSuppliersFromLpgys()
                && StringUtils.hasText(u9.getDecisionApiUrl())
                && StringUtils.hasText(u9.getSupplierReportPath());
    }

    private U9MaterialSyncResult mergeLpgysPhase(SrmProperties.U9 u9, U9MaterialSyncResult materialResult) {
        List<String> allErrors = new ArrayList<>(materialResult.errors());
        int tried = 0;
        int linksUpserted = 0;
        String reportPath = u9.getSupplierReportPath().trim();
        int p = 0;
        final int pageSize = 300;
        while (true) {
            Page<MaterialItem> chunk = materialItemRepository.findAll(
                    PageRequest.of(p, pageSize, Sort.by("id").ascending()));
            if (chunk.isEmpty()) {
                break;
            }
            for (MaterialItem m : chunk.getContent()) {
                if (m == null || !StringUtils.hasText(m.getCode())) {
                    continue;
                }
                tried++;
                try {
                    String raw = u9DecisionClient.postDecision(u9, reportPath, buildLpgysParameters(m.getCode()), 1, -1);
                    if (raw == null || raw.isBlank()) {
                        allErrors.add("lpgys 料号 " + m.getCode() + "：接口返回空");
                        continue;
                    }
                    List<U9LpgysSupplierRow> supplierRows = parseLpgysRowsFromResponse(raw);
                    U9MaterialSupplierWriter.Outcome o = materialSupplierWriter.replaceSuppliersForMaterial(
                            m.getId(), supplierRows);
                    linksUpserted += o.linksSaved();
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    allErrors.add("lpgys 料号 " + m.getCode() + "：" + msg);
                    log.warn("lpgys 同步失败 code={}", m.getCode(), e);
                }
            }
            if (!chunk.hasNext()) {
                break;
            }
            p++;
        }
        log.info("U9 lpgys 供应商同步完成：物料请求={} 供应商行写入≈{}（含多行）", tried, linksUpserted);
        return new U9MaterialSyncResult(
                materialResult.total(),
                materialResult.created(),
                materialResult.updated(),
                materialResult.skipped(),
                List.copyOf(allErrors),
                tried,
                linksUpserted);
    }

    private static List<Map<String, Object>> buildLpgysParameters(String materialCode) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "code");
        p.put("type", "String");
        p.put("value", materialCode != null ? materialCode : "");
        parameters.add(p);
        return parameters;
    }

    /**
     * 解析 lpgys 返回（与 wuliao 相同 envelope：data 对象数组或 data.rows 等）。
     */
    List<U9LpgysSupplierRow> parseLpgysRowsFromResponse(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.startsWith("<") || trimmed.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
                throw new BadRequestException("lpgys 接口返回了 HTML 而非 JSON");
            }
            JsonNode root = MAPPER.readTree(trimmed);
            FineReportJson.assertSuccess(root);
            if (root.has("data") && root.get("data").isTextual()) {
                String inner = root.get("data").asText().trim();
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    return parseLpgysRowsFromResponse(inner);
                }
            }
            JsonNode array = FineReportJson.locateObjectRowArray(root);
            if (array == null || !array.isArray()) {
                throw new BadRequestException("lpgys 响应中未找到供应商对象数组");
            }
            List<U9LpgysSupplierRow> out = new ArrayList<>();
            for (JsonNode row : array) {
                if (row != null && row.isObject()) {
                    out.add(MAPPER.convertValue(row, U9LpgysSupplierRow.class));
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析 lpgys JSON 失败: " + e.getOriginalMessage());
        }
    }

    /**
     * 帆软分页：多次 POST，合并 {@code data} 行，直到 {@code total_page_number} 或末页。
     */
    private List<U9MaterialSyncRow> fetchAllRowsFromFineReport(SrmProperties.U9 u9) {
        int perPage = u9.getSyncPageSize();
        if (perPage <= 0) {
            String raw = postFineReportDecisionPage(u9, u9.getPageNumber(), u9.getPageSize());
            if (raw == null || raw.isBlank()) {
                throw new BadRequestException("帆软接口返回空内容");
            }
            return parseMaterialRowsFromResponse(raw);
        }
        List<U9MaterialSyncRow> all = new ArrayList<>();
        int pageNum = 1;
        int totalPagesHint = -1;
        final int maxPages = 10_000;
        while (pageNum <= maxPages) {
            String raw = postFineReportDecisionPage(u9, pageNum, perPage);
            if (raw == null || raw.isBlank()) {
                break;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(raw.trim());
            } catch (JsonProcessingException e) {
                throw new BadRequestException("帆软第 " + pageNum + " 页响应非合法 JSON: " + e.getOriginalMessage());
            }
            if (totalPagesHint < 0 && root.has("total_page_number")) {
                JsonNode tp = root.get("total_page_number");
                if (tp.isNumber()) {
                    totalPagesHint = tp.asInt();
                }
            }
            List<U9MaterialSyncRow> pageRows = parseMaterialRowsFromResponse(raw);
            log.debug("U9 帆软分页 page={} rows={} perPage={} total_page_number={}",
                    pageNum, pageRows.size(), perPage, totalPagesHint >= 0 ? totalPagesHint : "n/a");
            all.addAll(pageRows);
            // 服务端若忽略分页一次返回全量，条数会大于 perPage，避免继续请求空页/重复
            if (pageRows.size() > perPage) {
                log.info("U9 帆软第 {} 页返回 {} 条 > perPage {}，视为未分页，停止翻页",
                        pageNum, pageRows.size(), perPage);
                break;
            }
            // total_page_number==0 时不能当作「共 0 页」提前结束，仅在 >0 时作为总页数
            if (totalPagesHint > 0 && pageNum >= totalPagesHint) {
                break;
            }
            if (pageRows.isEmpty()) {
                break;
            }
            if (pageRows.size() < perPage) {
                break;
            }
            pageNum++;
        }
        log.info("U9 帆软物料拉取合并完成：共 {} 条（sync-page-size={}）", all.size(), perPage);
        return all;
    }

    private String postFineReportDecisionPage(SrmProperties.U9 u9, int pageNumber, int pageSize) {
        String path = StringUtils.hasText(u9.getReportPath()) ? u9.getReportPath() : "API/wuliao.cpt";
        return u9DecisionClient.postDecision(u9, path, buildFineReportParameters(u9), pageNumber, pageSize);
    }

    private static List<Map<String, Object>> buildFineReportParameters(SrmProperties.U9 u9) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (u9.getFineReportParameters() == null || u9.getFineReportParameters().isEmpty()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", "pinming");
            p.put("type", "String");
            p.put("value", "");
            parameters.add(p);
            return parameters;
        }
        for (SrmProperties.FineReportParameter fp : u9.getFineReportParameters()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", fp.getName() != null ? fp.getName() : "");
            p.put("type", StringUtils.hasText(fp.getType()) ? fp.getType() : "String");
            p.put("value", fp.getValue() != null ? fp.getValue() : "");
            parameters.add(p);
        }
        return parameters;
    }

    private String getWithOptionalAuth(SrmProperties.U9 u9, String url) {
        try {
            RestClient client = RestClient.builder()
                    .requestFactory(u9HttpRequestFactory(u9))
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            var spec = client.get().uri(url);
            if (StringUtils.hasText(u9.getHttpUser())) {
                spec = spec.headers(h -> h.setBasicAuth(u9.getHttpUser(), passwordOrEmpty(u9.getHttpPassword()),
                        StandardCharsets.UTF_8));
            }
            return spec.retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            String errPayload = e.getResponseBodyAsString();
            String hint = errPayload != null && !errPayload.isBlank() ? truncateForMessage(errPayload, 800) : "";
            throw new BadRequestException("U9 物料 HTTP " + e.getStatusCode().value()
                    + (hint.isEmpty() ? ": " + e.getMessage() : "，响应: " + hint));
        } catch (RestClientException e) {
            throw new BadRequestException("U9 物料接口请求失败: " + e.getMessage());
        }
    }

    private static String passwordOrEmpty(String p) {
        return p != null ? p : "";
    }

    /** 帆软取数可能较慢，单独拉长读超时（见 srm.u9.http-read-timeout-ms） */
    private static ClientHttpRequestFactory u9HttpRequestFactory(SrmProperties.U9 u9) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Math.max(1_000, u9.getHttpConnectTimeoutMs()));
        f.setReadTimeout(Math.max(5_000, u9.getHttpReadTimeoutMs()));
        return f;
    }

    /**
     * 直接应用行数据（用于网关/中间件转 JSON 后推送，无需 enabled）。
     * 每行独立事务写入，避免在同一事务内捕获持久化异常导致整批 rollback-only。
     */
    public U9MaterialSyncResult apply(List<U9MaterialSyncRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return new U9MaterialSyncResult(0, 0, 0, 0, List.of());
        }
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            U9MaterialSyncRow row = rows.get(i);
            int line = i + 1;
            if (row == null || !StringUtils.hasText(row.getCode())) {
                errors.add("第 " + line + " 行：物料编码不能为空");
                skipped++;
                continue;
            }
            String code = row.getCode().trim();
            String name = StringUtils.hasText(row.getName()) ? row.getName().trim() : "";
            if (!StringUtils.hasText(name)) {
                errors.add("第 " + line + " 行（编码 " + code + "）：名称不能为空");
                skipped++;
                continue;
            }
            String uom = StringUtils.hasText(row.getUom()) ? row.getUom().trim() : "PCS";
            try {
                U9MaterialRowWriter.Outcome o = materialRowWriter.upsert(code, name, uom, row);
                if (o == U9MaterialRowWriter.Outcome.UPDATED) {
                    updated++;
                } else {
                    created++;
                }
            } catch (Exception e) {
                errors.add("第 " + line + " 行（编码 " + code + "）：" + e.getMessage());
                skipped++;
            }
        }
        log.info("U9 物料落库完成：total={} created={} updated={} skipped={} errorMessages={}",
                rows.size(), created, updated, skipped, errors.size());
        if (!errors.isEmpty()) {
            int cap = Math.min(5, errors.size());
            for (int i = 0; i < cap; i++) {
                log.warn("U9 物料行：{}", errors.get(i));
            }
            if (errors.size() > cap) {
                log.warn("U9 物料行：另有 {} 条错误未逐条打印", errors.size() - cap);
            }
        }
        return new U9MaterialSyncResult(rows.size(), created, updated, skipped, errors);
    }

    String resolveMaterialUrl(SrmProperties.U9 u9) {
        if (StringUtils.hasText(u9.getMaterialSyncUrl())) {
            return u9.getMaterialSyncUrl().trim();
        }
        if (!StringUtils.hasText(u9.getBaseUrl())) {
            return "";
        }
        String base = u9.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StringUtils.hasText(u9.getMaterialApiPath()) ? u9.getMaterialApiPath().trim() : "wuliao.cpt";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    /**
     * 解析帆软 Decision 返回或通用 JSON（数组 / data 数组 / columns+rows 表格）。
     */
    List<U9MaterialSyncRow> parseMaterialRowsFromResponse(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("\uFEFF")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.startsWith("<") || trimmed.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
                throw new BadRequestException("接口返回了 HTML 而非 JSON（多为登录页、404 或网关拦截），请检查 URL、鉴权与 VPN");
            }
            JsonNode root = MAPPER.readTree(trimmed);
            FineReportJson.assertSuccess(root);
            // 帆软偶发将 data 再包一层 JSON 字符串
            if (root.has("data") && root.get("data").isTextual()) {
                String inner = root.get("data").asText().trim();
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    return parseMaterialRowsFromResponse(inner);
                }
            }
            List<U9MaterialSyncRow> fromNested = tryParseDataRowsObjectArray(root);
            if (fromNested != null) {
                return fromNested;
            }
            List<U9MaterialSyncRow> fromGrid = tryParseColumnsRowsGrid(root);
            if (fromGrid != null) {
                return fromGrid;
            }
            return parseJsonRowsArray(root);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析物料 JSON 失败: " + e.getOriginalMessage());
        }
    }

    /**
     * {@code data: { "rows": [ {...}, ... ] }}（无 columns 时常见）。
     */
    private List<U9MaterialSyncRow> tryParseDataRowsObjectArray(JsonNode root) {
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            return null;
        }
        JsonNode rows = data.get("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return null;
        }
        if (!rows.get(0).isObject()) {
            return null;
        }
        List<U9MaterialSyncRow> out = new ArrayList<>();
        for (JsonNode row : rows) {
            if (row.isObject()) {
                out.add(MAPPER.convertValue(row, U9MaterialSyncRow.class));
            }
        }
        return out;
    }

    private static String truncateForMessage(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /**
     * 帆软常见：data 为对象且含 columns + rows（行为二维数组）。
     */
    private List<U9MaterialSyncRow> tryParseColumnsRowsGrid(JsonNode root) {
        JsonNode data = root.get("data");
        if (data != null && data.isObject() && data.has("rows")) {
            JsonNode cols = data.get("columns");
            JsonNode rows = data.get("rows");
            if (rows != null && rows.isArray() && !rows.isEmpty()) {
                return buildRowsFromGrid(cols, rows);
            }
        }
        if (root.isObject() && root.has("rows")) {
            JsonNode rows = root.get("rows");
            if (rows != null && rows.isArray() && !rows.isEmpty()) {
                return buildRowsFromGrid(root.get("columns"), rows);
            }
        }
        return null;
    }

    private List<U9MaterialSyncRow> buildRowsFromGrid(JsonNode columnsNode, JsonNode rowsNode) {
        if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty()) {
            return List.of();
        }
        List<String> colNames = new ArrayList<>();
        int dataStart = 0;
        if (columnsNode != null && columnsNode.isArray() && !columnsNode.isEmpty()) {
            columnsNode.forEach(c -> colNames.add(c.asText()));
        } else if (rowsNode.get(0).isArray()) {
            // 无 columns 时：第一行作为表头（帆软部分导出格式）
            JsonNode header = rowsNode.get(0);
            for (JsonNode h : header) {
                colNames.add(h.asText());
            }
            dataStart = 1;
        } else {
            return List.of();
        }
        List<U9MaterialSyncRow> out = new ArrayList<>();
        for (int r = dataStart; r < rowsNode.size(); r++) {
            JsonNode row = rowsNode.get(r);
            if (!row.isArray()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < colNames.size() && i < row.size(); i++) {
                map.put(colNames.get(i), nodeToJava(row.get(i)));
            }
            out.add(MAPPER.convertValue(map, U9MaterialSyncRow.class));
        }
        return out;
    }

    private static Object nodeToJava(JsonNode cell) {
        if (cell == null || cell.isNull()) {
            return null;
        }
        if (cell.isNumber()) {
            return cell.decimalValue();
        }
        if (cell.isTextual()) {
            return cell.asText();
        }
        if (cell.isBoolean()) {
            return cell.asBoolean();
        }
        return cell.asText();
    }

    private List<U9MaterialSyncRow> parseJsonRowsArray(JsonNode root) {
        JsonNode array = null;
        if (root.isArray()) {
            array = root;
        } else {
            for (String key : List.of("data", "items", "records", "rows", "Data", "Rows")) {
                if (root.has(key) && root.get(key).isArray()) {
                    array = root.get(key);
                    break;
                }
            }
        }
        if (array == null) {
            throw new BadRequestException(
                    "响应需为物料对象数组 [...]，或 {data:[...]}，或帆软 {data:{columns:[],rows:[]}}");
        }
        return MAPPER.convertValue(array, new TypeReference<List<U9MaterialSyncRow>>() {});
    }

}
