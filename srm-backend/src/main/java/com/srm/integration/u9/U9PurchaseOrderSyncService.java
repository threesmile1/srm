package com.srm.integration.u9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.srm.po.domain.PurchaseOrder;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从帆软 Decision 拉取 U9 已审核未关闭采购订单（caigou_cp.cpt），写入 SRM 并自动发布。
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
            int ordersCreated,
            int ordersUpdated,
            int skipped,
            List<String> errors) {}

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
            return new U9PurchaseOrderSyncResult(0, 0, 0, 0, List.of());
        }
        Map<String, List<JsonNode>> byDoc = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String docNo = normalizeDocNo(row);
            String orgKey = normalizeAccountOrg(row);
            if (!StringUtils.hasText(docNo) || !StringUtils.hasText(orgKey)) {
                continue;
            }
            String g = orgKey + "\t" + docNo;
            byDoc.computeIfAbsent(g, k -> new ArrayList<>()).add(row);
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
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
                log.warn("U9 PO 同步失败 docNo={} org={}", docNo, accountOrg, ex);
            }
        }
        log.info("U9 采购订单同步完成：rows={} groups={} created={} updated={} skipped={}",
                rows.size(), byDoc.size(), created, updated, skipped);
        return new U9PurchaseOrderSyncResult(rows.size(), created, updated, skipped, errors);
    }

    /** @return true if newly inserted, false if updated existing */
    private boolean upsertOneDocument(String accountOrgStr, String docNo, List<JsonNode> docRows) {
        OrgUnit org = orgUnitRepository
                .findFirstByOrgTypeAndU9OrgCode(OrgUnitType.PROCUREMENT, accountOrgStr.trim())
                .orElseThrow(() -> new BadRequestException(
                        "未找到采购组织：请在 org_unit.u9_org_code 配置核算组织 " + accountOrgStr));
        Warehouse wh = pickDefaultWarehouse(org);
        JsonNode first = docRows.get(0);
        String supplierCode = requireText(first, "供应商编码", "supplier_code", "Supplier_Code", "gysbm");
        String supplierName = firstText(first, "供应商名称", "supplier_name", "Supplier_Name", "gongyingshang");
        masterDataService.upsertSupplierMasterForU9(supplierCode, supplierName);
        Supplier supplier = supplierRepository.findByCode(supplierCode.trim())
                .orElseThrow(() -> new BadRequestException("供应商主档不存在: " + supplierCode));
        masterDataService.assertSupplierAuthorizedForOrg(supplier, org);
        masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);

        String currency = firstText(first, "currency", "币种", "Currency");
        if (!StringUtils.hasText(currency)) {
            currency = "CNY";
        }
        String remark = buildRemark(first, docNo);

        List<CreateLine> lines = new ArrayList<>();
        Set<String> seenLineKeys = new LinkedHashSet<>();
        for (JsonNode row : docRows) {
            String matCode = requireText(row, "物料编码", "料品编码", "ItemCode", "item_code", "code", "liaohao");
            String lineDedup = firstText(row, "POLine_ID", "poline_id", "PO_Line_ID");
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
                org.getId(), docNo, supplier.getId(), currency, remark, lines);
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
                if (rows != null && rows.isArray() && !rows.isEmpty() && rows.get(0).isObject()) {
                    List<JsonNode> out = new ArrayList<>();
                    for (JsonNode n : rows) {
                        if (n.isObject()) {
                            out.add(n);
                        }
                    }
                    return out;
                }
            }
            throw new BadRequestException("采购订单帆软响应中未找到对象行数组");
        } catch (JsonProcessingException e) {
            throw new BadRequestException("解析采购订单 JSON 失败: " + e.getOriginalMessage());
        }
    }

    private static String normalizeDocNo(JsonNode row) {
        return firstText(row, "单据编号", "DocNo", "doc_no", "PO_DOC_NO", "采购订单号");
    }

    private static String normalizeAccountOrg(JsonNode row) {
        for (String k : List.of("核算组织", "AccountOrg", "account_org", "Account_Org")) {
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            if (v.isNumber()) {
                if (v.isIntegralNumber()) {
                    return String.valueOf(v.asLong());
                }
                return v.decimalValue().toPlainString();
            }
            String t = v.asText();
            if (StringUtils.hasText(t)) {
                return t.trim();
            }
        }
        return "";
    }

    private static int lineNoFromRow(JsonNode row) {
        for (String k : List.of("行号", "DocLineNo", "line_no", "LineNo")) {
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            try {
                if (v.isIntegralNumber()) {
                    return v.asInt();
                }
                if (v.isNumber()) {
                    return v.decimalValue().setScale(0, RoundingMode.DOWN).intValue();
                }
                String t = v.asText().trim();
                if (StringUtils.hasText(t)) {
                    return new BigDecimal(t).setScale(0, RoundingMode.DOWN).intValue();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String buildRemark(JsonNode first, String docNo) {
        String seg = firstText(first, "来源正式订单号_全局段5", "来源正式订单号", "DescFlexField_PrivateDescSeg5");
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
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            try {
                if (v.isNumber()) {
                    return v.decimalValue();
                }
                String t = v.asText().trim();
                if (StringUtils.hasText(t)) {
                    return new BigDecimal(t);
                }
            } catch (Exception ignored) {
                // try next key
            }
        }
        throw new BadRequestException("缺少或无效数值（" + String.join("/", keys) + "）");
    }

    private static BigDecimal optionalDecimal(JsonNode row, String... keys) {
        for (String k : keys) {
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            try {
                if (v.isNumber()) {
                    return v.decimalValue();
                }
                String t = v.asText().trim();
                if (StringUtils.hasText(t)) {
                    return new BigDecimal(t);
                }
            } catch (Exception ignored) {
                // next key
            }
        }
        return null;
    }

    private static LocalDate parseDate(JsonNode row, String... keys) {
        for (String k : keys) {
            if (!row.has(k) || row.get(k).isNull()) {
                continue;
            }
            JsonNode v = row.get(k);
            if (v.isNumber() && v.isIntegralNumber()) {
                long ms = v.asLong();
                if (ms > 1_000_000_000_000L) {
                    return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
            }
            String t = textValue(v).trim();
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
}
