package com.srm.integration.u9;

import com.fasterxml.jackson.databind.JsonNode;
import com.srm.config.SrmProperties;
import com.srm.execution.domain.AsnLine;
import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.GoodsReceiptLine;
import com.srm.execution.domain.GrStatus;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.execution.service.GrNumberAllocator;
import com.srm.execution.service.GoodsReceiptService;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.foundation.util.NingboProcurementOrg;
import com.srm.po.domain.ExportStatus;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 宁波公司：从帆软 {@code API/shouhuo_nb.cpt} 拉取 U9 收货单写入 SRM（幂等键：采购组织 + u9_doc_no）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class U9GoodsReceiptSyncService {

    private final SrmProperties properties;
    private final U9DecisionClient u9DecisionClient;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GrNumberAllocator grNumberAllocator;
    private final GoodsReceiptService goodsReceiptService;

    public record U9GoodsReceiptSyncResult(
            int rowCount,
            int droppedUnmappedRows,
            int groupsTotal,
            int created,
            int updatedStatusOnly,
            int skippedNonNingbo,
            int skipped,
            List<String> errors) {}

    @Transactional
    public U9GoodsReceiptSyncResult fetchAndApply(Long procurementOrgId) {
        SrmProperties.U9 u9 = properties.getU9();
        if (!u9.isEnabled()) {
            throw new BadRequestException("未启用 U9 拉取：请配置 srm.u9.enabled=true");
        }
        if (!StringUtils.hasText(u9.getDecisionApiUrl())) {
            throw new BadRequestException("请配置 srm.u9.decision-api-url");
        }
        if (procurementOrgId == null) {
            throw new BadRequestException("缺少 procurementOrgId");
        }
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new BadRequestException("采购组织不存在: " + procurementOrgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        if (!NingboProcurementOrg.isNingbo(org)) {
            throw new BadRequestException("仅宁波公司支持从 U9 同步收货单");
        }

        String path = StringUtils.hasText(u9.getGoodsReceiptReportPath())
                ? u9.getGoodsReceiptReportPath().trim()
                : "API/shouhuo_nb.cpt";
        String raw = u9DecisionClient.postDecision(u9, path, buildGoodsReceiptParameters(u9), 1, -1);
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("帆软收货单接口返回空内容");
        }
        List<JsonNode> rows = FineReportObjectRowParser.parseObjectRows(raw);
        if (rows.isEmpty()) {
            return new U9GoodsReceiptSyncResult(0, 0, 0, 0, 0, 0, 0, List.of());
        }

        Map<String, List<JsonNode>> byDoc = new LinkedHashMap<>();
        int dropped = 0;
        for (JsonNode row : rows) {
            String docNo = normalizeU9GrDocNo(row);
            if (!StringUtils.hasText(docNo)) {
                dropped++;
                continue;
            }
            byDoc.computeIfAbsent(docNo, k -> new ArrayList<>()).add(row);
        }

        int created = 0;
        int updated = 0;
        int skippedNb = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        if (!rows.isEmpty() && byDoc.isEmpty()) {
            errors.add("帆软 " + rows.size()
                    + " 行均未归组：缺少「收货单号」列/值，或列名与程序约定不一致。首行字段名示例："
                    + sampleRowFieldNames(rows.get(0)));
        }

        for (Map.Entry<String, List<JsonNode>> e : byDoc.entrySet()) {
            String u9DocNo = e.getKey();
            List<JsonNode> docRows = new ArrayList<>(e.getValue());
            docRows.sort(Comparator.comparingInt(U9GoodsReceiptSyncService::lineNoFromRow));
            try {
                int r = upsertOneDocument(org, u9DocNo.trim(), docRows);
                if (r == 1) {
                    created++;
                } else if (r == 2) {
                    updated++;
                }
            } catch (Exception ex) {
                skipped++;
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.add("U9 收货 " + u9DocNo + "：" + msg);
                log.warn("U9 收货单同步失败 u9DocNo={}", u9DocNo, ex);
            }
        }
        log.info("U9 收货单同步完成：rows={} groups={} created={} updatedStatus={} skippedNonNingbo={} skipped={}",
                rows.size(), byDoc.size(), created, updated, skippedNb, skipped);
        return new U9GoodsReceiptSyncResult(
                rows.size(), dropped, byDoc.size(), created, updated, skippedNb, skipped, errors);
    }

    private static List<Map<String, Object>> buildGoodsReceiptParameters(SrmProperties.U9 u9) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (u9.getGoodsReceiptFineReportParameters() != null && !u9.getGoodsReceiptFineReportParameters().isEmpty()) {
            for (SrmProperties.FineReportParameter fp : u9.getGoodsReceiptFineReportParameters()) {
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

    /** 1=created, 2=status-only update */
    private int upsertOneDocument(OrgUnit org, String u9DocNo, List<JsonNode> docRows) {
        JsonNode first = docRows.get(0);
        String u9Status = firstText(first,
                "单据状态", "状态", "U9状态", "doc_status", "业务状态", "status", "STATUS");
        LocalDate receiptDate = parseReceiptDate(first);

        GoodsReceipt existing = goodsReceiptRepository
                .findByProcurementOrg_IdAndU9DocNo(org.getId(), u9DocNo)
                .orElse(null);
        if (existing != null) {
            existing.setU9Status(StringUtils.hasText(u9Status) ? u9Status.trim() : existing.getU9Status());
            if (receiptDate != null) {
                existing.setReceiptDate(receiptDate);
            }
            goodsReceiptRepository.save(existing);
            return 2;
        }

        PurchaseOrder po = resolvePurchaseOrder(org, first, u9DocNo);
        if (po.getStatus() != PoStatus.RELEASED && po.getStatus() != PoStatus.CLOSED) {
            throw new BadRequestException("关联采购订单状态不可收货: " + po.getStatus());
        }
        long poId = po.getId();
        po = purchaseOrderRepository.findWithDetailsById(poId)
                .orElseThrow(() -> new BadRequestException("采购订单不存在: " + poId));
        Warehouse wh = resolveWarehouse(org, first);

        Map<Long, PurchaseOrderLine> polById = po.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));

        boolean enforceCap = properties.isEnforceMaxReceiveLimit();
        BigDecimal ratio = enforceCap ? BigDecimal.ONE.add(properties.getOverReceiveRatio()) : null;
        Map<Long, BigDecimal> qtyByPolId = new LinkedHashMap<>();
        for (JsonNode row : docRows) {
            PurchaseOrderLine pol = resolvePoLine(po, polById, row);
            BigDecimal qty = requirePositiveQty(row);
            qtyByPolId.merge(pol.getId(), qty, BigDecimal::add);
        }

        GoodsReceipt gr = new GoodsReceipt();
        gr.setGrNo(grNumberAllocator.nextGrNo(org));
        gr.setProcurementOrg(org);
        gr.setLedger(po.getLedger());
        gr.setSupplier(po.getSupplier());
        gr.setWarehouse(wh);
        gr.setPurchaseOrder(po);
        gr.setReceiptDate(receiptDate != null ? receiptDate : LocalDate.now());
        gr.setRemark(buildRemark(u9DocNo, first));
        gr.setStatus(GrStatus.APPROVED);
        gr.setExportStatus(ExportStatus.NOT_EXPORTED);
        gr.setSourceSystem("U9");
        gr.setU9DocNo(u9DocNo);
        gr.setU9Status(StringUtils.hasText(u9Status) ? u9Status.trim() : null);

        int lineNoSeq = 1;
        for (Map.Entry<Long, BigDecimal> en : qtyByPolId.entrySet()) {
            PurchaseOrderLine pol = polById.get(en.getKey());
            BigDecimal qty = en.getValue();
            BigDecimal recv = pol.getReceivedQty() != null ? pol.getReceivedQty() : BigDecimal.ZERO;
            if (enforceCap) {
                BigDecimal maxRecv = pol.getQty().multiply(ratio).setScale(4, RoundingMode.HALF_UP).subtract(recv);
                if (qty.compareTo(maxRecv) > 0) {
                    String poRef = po.getPoNo();
                    if (StringUtils.hasText(po.getU9DocNo()) && !po.getU9DocNo().equals(po.getPoNo())) {
                        poRef = poRef + "（U9 " + po.getU9DocNo() + "）";
                    }
                    throw new BadRequestException(
                            "采购订单 " + poRef + " 行 " + pol.getLineNo() + " 超过可收上限: " + maxRecv);
                }
            }
            AsnLine asn = goodsReceiptService.findBestAsnLineForPolOrNull(po, pol);
            GoodsReceiptLine gl = new GoodsReceiptLine();
            gl.setGoodsReceipt(gr);
            gl.setPurchaseOrderLine(pol);
            gl.setAsnLine(asn);
            gl.setLineNo(lineNoSeq++);
            gl.setReceivedQty(qty);
            gr.getLines().add(gl);
            pol.setReceivedQty(recv.add(qty));
            purchaseOrderLineRepository.save(pol);
        }
        if (gr.getLines().isEmpty()) {
            throw new BadRequestException("无有效收货行");
        }

        goodsReceiptRepository.save(gr);
        maybeClosePurchaseOrderIfFullyReceived(po);
        return 1;
    }

    private void maybeClosePurchaseOrderIfFullyReceived(PurchaseOrder po) {
        PurchaseOrder fresh = purchaseOrderRepository.findWithDetailsById(po.getId()).orElse(po);
        boolean allReceived = fresh.getLines().stream()
                .allMatch(l -> l.getReceivedQty() != null && l.getReceivedQty().compareTo(l.getQty()) >= 0);
        if (allReceived) {
            fresh.setStatus(PoStatus.CLOSED);
            purchaseOrderRepository.save(fresh);
        }
    }

    private PurchaseOrder resolvePurchaseOrder(OrgUnit org, JsonNode first, String u9GrDocNo) {
        // 帆软收货单报表中，「来源单号」(如 NBCG...) 才是要匹配 SRM PO 的 u9_doc_no；
        // 「来源正式订单号」(常带点号/段)不是采购订单号，需避免误取。
        String poRef = firstNonBlank(
                firstText(first,
                        "来源单号", "来源订单号", "来源采购单号",
                        "来源采购订单号", "采购订单号",
                        "po_no", "PO_NO", "ref_po_no", "订单编号", "PO号"),
                firstTextByKeyContainsExcluding(first,
                        List.of("正式"),
                        "来源单号", "来源订单", "采购订单", "PO"));
        if (!StringUtils.hasText(poRef)) {
            throw new BadRequestException(
                    "缺少采购订单号：帆软行中未解析到来源单号/来源采购订单号/po_no/订单编号 等（采购订单号为空）。"
                            + " U9收货单号=" + u9GrDocNo
                            + "；请核对模板列名。当前首行字段示例：" + sampleRowFieldNames(first));
        }
        String key = poRef.trim();
        return purchaseOrderRepository.findByProcurementOrg_IdAndU9DocNo(org.getId(), key)
                .or(() -> purchaseOrderRepository.findByProcurementOrg_IdAndPoNo(org.getId(), key))
                .orElseThrow(() -> new BadRequestException(
                        "未找到采购订单（采购订单号=" + key + "；请确认 SRM 已同步 PO，且 PO 的 u9_doc_no 或 po_no 能匹配该值）"));
    }

    private PurchaseOrderLine resolvePoLine(PurchaseOrder po, Map<Long, PurchaseOrderLine> polById, JsonNode row) {
        int docLineNo = lineNoFromRow(row);
        if (docLineNo != Integer.MAX_VALUE) {
            for (PurchaseOrderLine l : po.getLines()) {
                if (l.getLineNo() == docLineNo) {
                    return l;
                }
            }
        }
        String mat = firstText(row, "物料编码", "料号", "material_code", "MaterialCode", "ITEM_CODE");
        if (StringUtils.hasText(mat)) {
            String code = mat.trim();
            for (PurchaseOrderLine l : po.getLines()) {
                if (l.getMaterial() != null && code.equalsIgnoreCase(l.getMaterial().getCode())) {
                    return l;
                }
            }
        }
        throw new BadRequestException("无法匹配采购订单行（请提供行号或物料编码列）");
    }

    private Warehouse resolveWarehouse(OrgUnit org, JsonNode row) {
        String code = firstText(row, "仓库编码", "warehouse_code", "收货仓库", "cangku", "CK", "WH_CODE");
        List<Warehouse> list = warehouseRepository.findByProcurementOrgOrderByCodeAsc(org);
        if (list.isEmpty()) {
            throw new BadRequestException("采购组织下无仓库");
        }
        if (!StringUtils.hasText(code)) {
            return list.get(0);
        }
        String c = code.trim();
        for (Warehouse w : list) {
            if (c.equalsIgnoreCase(w.getCode())) {
                return w;
            }
            if (w.getU9WhCode() != null && c.equalsIgnoreCase(w.getU9WhCode().trim())) {
                return w;
            }
        }
        return list.get(0);
    }

    private static String buildRemark(String u9DocNo, JsonNode first) {
        String extra = firstText(first, "备注", "remark", "REMARK");
        if (StringUtils.hasText(extra)) {
            return "U9 " + u9DocNo + " | " + extra.trim();
        }
        return "U9 同步 " + u9DocNo;
    }

    private static String sampleRowFieldNames(JsonNode row) {
        if (row == null || !row.isObject()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        Iterator<String> it = row.fieldNames();
        while (it.hasNext() && n < 30) {
            if (n > 0) {
                sb.append(", ");
            }
            sb.append(it.next());
            n++;
        }
        if (it.hasNext()) {
            sb.append(" …");
        }
        return sb.toString();
    }

    private static String normalizeU9GrDocNo(JsonNode row) {
        return firstNonBlank(
                firstText(row,
                        "收货单号", "收货单编号", "GR单号", "gr_no", "GR_NO", "U9收货单号",
                        "DocNo", "doc_no", "DOC_NO", "单据编号", "单据号", "单号", "BillNo", "bill_no"),
                firstTextByKeyContains(row, "收货单号", "收货单", "GR单", "GR_NO", "GR单号"));
    }

    // 核算组织列已取消：以接口入参 procurementOrgId 为准

    private static int lineNoFromRow(JsonNode row) {
        for (String k : List.of("订单行号", "行号", "DocLineNo", "line_no", "LineNo", "po_line_no", "POLineNo")) {
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                return new BigDecimal(t.trim()).setScale(0, RoundingMode.DOWN).intValue();
            } catch (Exception ignored) {
                // next
            }
        }
        return Integer.MAX_VALUE;
    }

    private static LocalDate parseReceiptDate(JsonNode row) {
        for (String k : List.of("收货日期", "单据日期", "业务日期", "receipt_date", "ReceiptDate", "GRDate")) {
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
                    return java.time.LocalDateTime.parse(t.trim(), fmt).toLocalDate();
                } catch (DateTimeParseException ignored) {
                    // next
                }
            }
        }
        return null;
    }

    private static BigDecimal requirePositiveQty(JsonNode row) {
        for (String k : List.of("实收数量", "收货数量", "数量", "qty", "QTY", "qty_received", "received_qty", "RecQty")) {
            String t = firstText(row, k);
            if (!StringUtils.hasText(t)) {
                continue;
            }
            try {
                BigDecimal d = new BigDecimal(t.trim());
                if (d.compareTo(BigDecimal.ZERO) > 0) {
                    return d;
                }
            } catch (Exception ignored) {
                // next
            }
        }
        throw new BadRequestException("缺少或无效收货数量（收货数量/qty 等）");
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c.trim();
            }
        }
        return "";
    }

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
            String lower = fn.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                String tl = t.toLowerCase(Locale.ROOT);
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

    private static String firstTextByKeyContainsExcluding(JsonNode row, List<String> excludeTokens, String... tokens) {
        if (row == null || !row.isObject() || tokens == null || tokens.length == 0) {
            return "";
        }
        Iterator<String> it = row.fieldNames();
        while (it.hasNext()) {
            String fn = it.next();
            if (!StringUtils.hasText(fn)) {
                continue;
            }
            if (excludeTokens != null && !excludeTokens.isEmpty()) {
                boolean excluded = false;
                for (String ex : excludeTokens) {
                    if (StringUtils.hasText(ex) && fn.contains(ex)) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) {
                    continue;
                }
            }
            String lower = fn.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                String tl = t.toLowerCase(Locale.ROOT);
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
}
