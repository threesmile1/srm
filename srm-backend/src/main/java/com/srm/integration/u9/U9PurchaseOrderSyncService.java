package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.srm.config.SrmProperties;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.SupplierRepository;
import com.srm.master.service.MasterDataService;
import com.srm.po.service.PurchaseOrderService;
import com.srm.po.service.PurchaseOrderService.CreateLine;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从帆软 Decision 拉取 U9 采购订单（caigou_cp.cpt），写入 SRM。
 * <p>
 * SRM 侧行为：
 * - 幂等键：采购组织 + U9 单据编号
 * - 已审核且未关闭/取消：自动发布给供应商
 * - 其他状态：也可同步入 SRM，但不会误发布；且若订单已有关联业务（ASN/收货）将跳过覆盖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class U9PurchaseOrderSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;
    private final PurchaseOrderService purchaseOrderService;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialItemRepository materialItemRepository;
    private final SupplierRepository supplierRepository;
    private final MasterDataService masterDataService;

    public record U9PurchaseOrderSyncResult(
            int rowCount,
            /** 无法归组（缺少单据编号或核算组织列）的帆软行数 */
            int droppedUnmappedRows,
            int groupsTotal,
            int ordersCreated,
            int ordersUpdated,
            int skipped,
            List<String> errors,
            /** 按异常信息前缀聚合（便于一眼看出共因，如「物料不存在」） */
            Map<String, Integer> errorReasonCounts) {}

    public U9PurchaseOrderSyncResult fetchAndApply() {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        if (!StringUtils.hasText(u9.getDecisionApiUrl())) {
            throw new BadRequestException("请配置 srm.u9.decision-api-url");
        }
        List<JsonNode> rows = fetchAllPages(u9);
        if (rows.isEmpty()) {
            return new U9PurchaseOrderSyncResult(0, 0, 0, 0, 0, 0, List.of(), Map.of());
        }
        Map<String, List<JsonNode>> byDoc = new LinkedHashMap<>();
        int droppedUnmappedRows = 0;
        for (JsonNode row : rows) {
            String docNo = normalizeDocNo(row);
            String orgKey = normalizeAccountOrg(row);
            if (!StringUtils.hasText(docNo) || !StringUtils.hasText(orgKey)) {
                droppedUnmappedRows++;
                continue;
            }
            String g = orgKey + "\t" + docNo;
            byDoc.computeIfAbsent(g, k -> new ArrayList<>()).add(row);
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();
        for (Map.Entry<String, List<JsonNode>> e : byDoc.entrySet()) {
            String[] parts = e.getKey().split("\t", 2);
            String accountOrg = parts[0];
            String docNo = parts[1];
            List<JsonNode> docRows = new ArrayList<>(e.getValue());
            docRows.sort(Comparator.comparingInt(U9PurchaseOrderSyncService::lineNoFromRow));
            try {
                boolean wasNew = upsertOneDocument(accountOrg, docNo, docRows);
                if (wasNew) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception ex) {
                skipped++;
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.add("U9 " + docNo + "（组织 " + accountOrg + "）：" + msg);
                String reasonKey = summarizeReason(msg);
                reasonCounts.merge(reasonKey, 1, Integer::sum);
                log.warn("U9 PO 同步失败 docNo={} org={}", docNo, accountOrg, ex);
            }
        }
        log.info("U9 采购订单同步完成：rows={} groups={} created={} updated={} skipped={} errorReasonCounts={}",
                rows.size(), byDoc.size(), created, updated, skipped, reasonCounts);
        return new U9PurchaseOrderSyncResult(
                rows.size(), droppedUnmappedRows, byDoc.size(), created, updated, skipped, errors, reasonCounts);
    }

    /** 从单条异常信息提炼共因（用于聚合展示） */
    private static String summarizeReason(String msg) {
        if (msg == null || msg.isBlank()) {
            return "(无信息)";
        }
        if (msg.contains("物料不存在")) {
            return "物料不存在（请先做 U9 物料同步）";
        }
        if (msg.contains("未找到采购组织")) {
            return "未匹配 org_unit.u9_org_code（核算组织）";
        }
        if (msg.contains("供应商未授权")) {
            return "供应商未授权当前采购组织";
        }
        if (msg.contains("供应商状态")) {
            return "供应商生命周期不允许下单";
        }
        if (msg.contains("缺少字段")) {
            return "帆软列名与系统预期不一致（缺少字段）";
        }
        if (msg.contains("缺少或无效数值")) {
            return "数量/单价等数值列缺失或无法解析";
        }
        if (msg.contains("无有效订单行")) {
            return "无有效订单行（去重后为空或行被跳过）";
        }
        if (msg.contains("无仓库")) {
            return "采购组织下无仓库";
        }
        if (msg.contains("已有收货")) {
            return "订单已有收货禁止覆盖";
        }
        if (msg.contains("已有发货通知") || msg.contains("ASN")) {
            return "订单已有发货通知(ASN)禁止覆盖";
        }
        if (msg.length() > 120) {
            return msg.substring(0, 120) + "…";
        }
        return msg;
    }

    /** @return true if newly inserted, false if updated existing */
    private boolean upsertOneDocument(String accountOrgStr, String docNo, List<JsonNode> docRows) {
        OrgUnit org = orgUnitRepository
                .findFirstByOrgTypeAndU9OrgCode(OrgUnitType.PROCUREMENT, accountOrgStr.trim())
                .orElseThrow(() -> new BadRequestException(
                        "未找到采购组织：请在 org_unit.u9_org_code 配置核算组织 " + accountOrgStr));
        Warehouse wh = pickDefaultWarehouse(org);
        JsonNode first = docRows.get(0);
        String supplierCode = requireText(first,
                "供应商编码", "supplier_code", "Supplier_Code", "SUPPLIER_CODE", "gysbm", "GYSBM", "供应商代码");
        String supplierName = firstText(first,
                "供应商名称", "supplier_name", "Supplier_Name", "SUPPLIER_NAME", "gongyingshang", "Name", "NAME");
        masterDataService.upsertSupplierMasterForU9(supplierCode, supplierName);
        Supplier supplier = supplierRepository.fetchWithOrgsByCode(supplierCode.trim())
                .orElseThrow(() -> new BadRequestException("供应商主档不存在: " + supplierCode));
        masterDataService.assertSupplierAuthorizedForOrg(supplier, org);
        masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);

        String currency = firstText(first, "currency", "币种", "Currency");
        if (!StringUtils.hasText(currency)) {
            currency = "CNY";
        }
        String remark = buildRemark(first, docNo);
        String businessDateText = firstNonBlank(
                firstText(first, "业务日期", "BusinessDate", "business_date"),
                firstTextByKeyContains(first, "业务日期", "businessdate"));
        LocalDate businessDate = parseDateText(businessDateText);
        String officialOrderNo = firstNonBlank(
                firstText(first, "正式订单号", "DescFlexField_PrivateDescSeg5"),
                firstTextByKeyContains(first, "正式订单号", "订单号_正式", "official"));
        String store2 = firstNonBlank(
                firstText(first, "二级门店", "DescFlexField_PrivateDescSeg8"),
                firstTextByKeyContains(first, "二级门店", "门店"));
        String receiverName = firstNonBlank(
                firstText(first, "收货人名称", "DescFlexField_PrivateDescSeg3"),
                firstTextByKeyContains(first, "收货人", "收货人名称", "receiver"));
        String terminalPhone = firstNonBlank(
                firstText(first, "终端电话", "DescFlexField_PrivateDescSeg11"),
                firstTextByKeyContains(first, "终端电话", "电话", "phone", "tel"));
        String installAddress = firstNonBlank(
                firstText(first, "安装地址", "DescFlexField_PrivateDescSeg10"),
                firstTextByKeyContains(first, "安装地址", "地址", "address"));

        String docStatusText = firstNonBlank(
                firstText(first, "单据状态", "DocStatus", "doc_status", "status", "状态"),
                firstTextByKeyContains(first, "单据状态", "单据 状态", "状态", "docstatus"));

        List<CreateLine> lines = new ArrayList<>();
        Set<String> seenLineKeys = new LinkedHashSet<>();
        for (JsonNode row : docRows) {
            String matCode = requireText(row,
                    "物料编码", "料品编码", "ItemCode", "item_code", "ITEMCODE", "Item_Code",
                    "liaohao", "料号", "wlh", "物料代码");
            String lineDedup = firstText(row, "POLine_ID", "poline_id", "PO_Line_ID", "POLINE_ID");
            if (!StringUtils.hasText(lineDedup)) {
                lineDedup = matCode + "#" + lineNoFromRow(row);
            }
            if (!seenLineKeys.add(lineDedup)) {
                continue;
            }
            MaterialItem mat = materialItemRepository.findByCodeIgnoreCase(matCode.trim())
                    .or(() -> materialItemRepository.findFirstByU9ItemCodeIgnoreCase(matCode.trim()))
                    .orElseThrow(() -> new BadRequestException("物料不存在: " + matCode + "（请先 U9 同步物料）"));
            BigDecimal qty = requirePositiveDecimal(row, "销售数量", "PurQtyCU", "qty", "数量");
            BigDecimal unitPrice = requireNonNegativeDecimal(row, "最终价", "FinallyPriceTC", "unit_price", "单价");
            BigDecimal amountOverride = optionalDecimal(row, "价税合计", "TotalMnyTC", "amount", "金额");
            String uom = firstText(row, "销售单位", "uom", "UOM", "单位");
            LocalDate reqDate = parseDate(row, "要求交货日期", "DeliveryDate", "requested_date", "交期");

            lines.add(new CreateLine(
                    mat.getId(),
                    wh.getId(),
                    qty,
                    uom,
                    unitPrice,
                    reqDate,
                    amountOverride));
        }
        if (lines.isEmpty()) {
            throw new BadRequestException("无有效订单行");
        }
        boolean existed = purchaseOrderService.existsU9Document(org.getId(), docNo);
        purchaseOrderService.upsertFromU9AndRelease(
                org.getId(),
                docNo,
                supplier.getId(),
                currency,
                remark,
                businessDate,
                docStatusText,
                officialOrderNo,
                store2,
                receiverName,
                terminalPhone,
                installAddress,
                lines);
        return !existed;
    }

    private Warehouse pickDefaultWarehouse(OrgUnit org) {
        List<Warehouse> list = warehouseRepository.findByProcurementOrgOrderByCodeAsc(org);
        if (list.isEmpty()) {
            throw new BadRequestException("采购组织下无仓库，无法落采购订单行: " + org.getCode());
        }
        return list.get(0);
    }

    private List<JsonNode> fetchAllPages(SrmProperties.U9 u9) {
        int perPage = u9.getSyncPageSize();
        String path = StringUtils.hasText(u9.getPurchaseOrderReportPath())
                ? u9.getPurchaseOrderReportPath().trim()
                : "API/caigou_cp.cpt";
        if (perPage <= 0) {
            String raw = u9DecisionClient.postDecision(u9, path, buildPurchaseOrderParameters(u9),
                    u9.getPageNumber(), u9.getPageSize());
            if (raw == null || raw.isBlank()) {
                throw new BadRequestException("帆软采购订单接口返回空内容");
            }
            return parseObjectRows(raw);
        }
        List<JsonNode> all = new ArrayList<>();
        int pageNum = 1;
        int totalPagesHint = -1;
        final int maxPages = 10_000;
        while (pageNum <= maxPages) {
            String raw = u9DecisionClient.postDecision(u9, path, buildPurchaseOrderParameters(u9), pageNum, perPage);
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
                if (tp != null && tp.isNumber()) {
                    totalPagesHint = tp.asInt();
                }
            }
            List<JsonNode> pageRows = parseObjectRows(raw);
            log.debug("U9 采购订单帆软分页 page={} rows={} perPage={} total_page_number={}",
                    pageNum, pageRows.size(), perPage, totalPagesHint >= 0 ? totalPagesHint : "n/a");
            all.addAll(pageRows);
            if (pageRows.size() > perPage) {
                break;
            }
            if (pageRows.size() < perPage) {
                break;
            }
            if (totalPagesHint > 0 && pageNum >= totalPagesHint) {
                break;
            }
            pageNum++;
        }
        log.info("U9 采购订单帆软拉取合并完成：共 {} 行（sync-page-size={}）", all.size(), perPage);
        return all;
    }

    private static List<Map<String, Object>> buildPurchaseOrderParameters(SrmProperties.U9 u9) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (u9.getPurchaseOrderFineReportParameters() != null && !u9.getPurchaseOrderFineReportParameters().isEmpty()) {
            for (SrmProperties.FineReportParameter fp : u9.getPurchaseOrderFineReportParameters()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", fp.getName() != null ? fp.getName() : "");
                p.put("type", StringUtils.hasText(fp.getType()) ? fp.getType() : "String");
                p.put("value", fp.getValue() != null ? fp.getValue() : "");
                parameters.add(p);
            }
            return parameters;
        }
        parameters.add(new LinkedHashMap<>());
        return parameters;
    }

    private List<JsonNode> parseObjectRows(String json) {
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
            throw new BadRequestException("采购订单帆软响应中未找到对象行数组（也不支持解析 columns+rows 网格）");
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析采购订单 JSON 失败: " + e.getOriginalMessage());
        }
    }

    /**
     * 帆软 data.rows 为二维数组时，按 columns 或首行表头转成对象行（与物料同步逻辑一致）。
     */
    private List<JsonNode> tryConvertColumnsRowsGrid(JsonNode columnsNode, JsonNode rowsNode) {
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

    private static String normalizeDocNo(JsonNode row) {
        return firstText(row,
                "单据编号", "DocNo", "doc_no", "DOCNO", "PO_DOC_NO", "采购订单号", "采购单号", "订单编号", "PO_NO", "pono");
    }

    private static String normalizeAccountOrg(JsonNode row) {
        for (String k : List.of(
                "核算组织", "采购组织",
                "AccountOrg", "account_org", "Account_Org", "ACCOUNTORG"
        )) {
            String raw = firstText(row, k);
            if (StringUtils.hasText(raw)) {
                return raw.trim();
            }
        }
        return "";
    }

    private static int lineNoFromRow(JsonNode row) {
        for (String k : List.of("行号", "DocLineNo", "line_no", "LineNo", "lineno", "LINE_NO")) {
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                return new BigDecimal(t.trim()).setScale(0, RoundingMode.DOWN).intValue();
            } catch (Exception ignored) {
                // next key
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String buildRemark(JsonNode first, String docNo) {
        String seg = firstText(first,
                "正式订单号",
                "来源正式订单号_全局段5", "来源正式订单号",
                "DescFlexField_PrivateDescSeg5");
        if (StringUtils.hasText(seg)) {
            return "U9 " + docNo + " | " + seg;
        }
        return "U9 同步 " + docNo;
    }

    private static String requireText(JsonNode row, String... keys) {
        String t = firstText(row, keys);
        if (!StringUtils.hasText(t)) {
            throw new BadRequestException("缺少字段（" + String.join("/", keys) + "）");
        }
        return t.trim();
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) return "";
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c.trim();
            }
        }
        return "";
    }

    /** 兼容帆软列名被加前后缀/空格/括号：只要列名包含 token 即可取值 */
    private static String firstTextByKeyContains(JsonNode row, String... tokens) {
        if (row == null || !row.isObject() || tokens == null || tokens.length == 0) {
            return "";
        }
        Iterator<String> it = row.fieldNames();
        while (it.hasNext()) {
            String fn = it.next();
            if (!StringUtils.hasText(fn)) {
                continue;
            }
            String lower = fn.toLowerCase();
            for (String t : tokens) {
                if (!StringUtils.hasText(t)) continue;
                String tl = t.toLowerCase();
                if (lower.contains(tl) || fn.contains(t)) {
                    JsonNode v = row.get(fn);
                    String tv = textValue(v);
                    if (StringUtils.hasText(tv)) {
                        return tv.trim();
                    }
                }
            }
        }
        return "";
    }

    private static String firstText(JsonNode row, String... keys) {
        for (String k : keys) {
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            String t = textValue(v);
            if (StringUtils.hasText(t)) {
                return t.trim();
            }
        }
        for (String k : keys) {
            String want = k.trim().toLowerCase(Locale.ROOT);
            Iterator<String> it = row.fieldNames();
            while (it.hasNext()) {
                String fn = it.next();
                if (fn != null && fn.trim().toLowerCase(Locale.ROOT).equals(want)) {
                    JsonNode v = row.get(fn);
                    if (v != null && !v.isNull()) {
                        String t = textValue(v);
                        if (StringUtils.hasText(t)) {
                            return t.trim();
                        }
                    }
                }
            }
        }
        return "";
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

    private static BigDecimal requirePositiveDecimal(JsonNode row, String... keys) {
        BigDecimal d = requireDecimal(row, keys);
        if (d.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("数量必须大于 0（" + String.join("/", keys) + "）");
        }
        return d;
    }

    private static BigDecimal requireNonNegativeDecimal(JsonNode row, String... keys) {
        BigDecimal d = requireDecimal(row, keys);
        if (d.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("单价不能为负（" + String.join("/", keys) + "）");
        }
        return d;
    }

    private static BigDecimal requireDecimal(JsonNode row, String... keys) {
        for (String k : keys) {
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                return new BigDecimal(t.trim());
            } catch (Exception ignored) {
                // try next key
            }
        }
        throw new BadRequestException("缺少或无效数值（" + String.join("/", keys) + "）");
    }

    private static BigDecimal optionalDecimal(JsonNode row, String... keys) {
        for (String k : keys) {
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                return new BigDecimal(t.trim());
            } catch (Exception ignored) {
                // next key
            }
        }
        return null;
    }

    private static LocalDate parseDate(JsonNode row, String... keys) {
        for (String k : keys) {
            if (row.has(k) && row.get(k).isNumber() && row.get(k).isIntegralNumber()) {
                long ms = row.get(k).asLong();
                if (ms > 1_000_000_000_000L) {
                    return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
            }
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            for (DateTimeFormatter fmt : List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"))) {
                try {
                    if (fmt == DateTimeFormatter.ISO_LOCAL_DATE) {
                        return LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t, fmt);
                    }
                    return java.time.LocalDateTime.parse(t, fmt).toLocalDate();
                } catch (DateTimeParseException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    /** 直接解析日期字符串（支持 yyyy-MM-dd 或带时间）。 */
    private static LocalDate parseDateText(String t) {
        if (!StringUtils.hasText(t)) {
            return null;
        }
        String s = t.trim();
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"))) {
            try {
                if (fmt == DateTimeFormatter.ISO_LOCAL_DATE) {
                    return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s, fmt);
                }
                return java.time.LocalDateTime.parse(s, fmt).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // next
            }
        }
        return null;
    }
}
