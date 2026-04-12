package com.srm.execution.service;

import com.srm.config.SrmProperties;
import com.srm.execution.domain.AsnLine;
import com.srm.execution.domain.AsnNotice;
import com.srm.execution.domain.AsnStatus;
import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.GoodsReceiptLine;
import com.srm.execution.repo.AsnLineRepository;
import com.srm.execution.repo.AsnNoticeRepository;
import com.srm.execution.repo.GoodsReceiptLineRepository;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.execution.web.GoodsReceiptSummaryResponse;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.notification.service.StaffNotificationService;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final AsnLineRepository asnLineRepository;
    private final AsnNoticeRepository asnNoticeRepository;
    private final GrNumberAllocator grNumberAllocator;
    private final SrmProperties srmProperties;
    private final StaffNotificationService staffNotificationService;

    @Transactional(readOnly = true)
    public List<GoodsReceipt> listByOrg(Long procurementOrgId) {
        return goodsReceiptRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
    }

    /**
     * 列表汇总：含关联订单「待收货」数量（未收清数量之和）。
     */
    @Transactional(readOnly = true)
    public List<GoodsReceiptSummaryResponse> listSummaryByOrg(Long procurementOrgId) {
        List<GoodsReceipt> list = goodsReceiptRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> poIds = list.stream().map(g -> g.getPurchaseOrder().getId()).collect(Collectors.toSet());
        Map<Long, BigDecimal> pendingByPo = new HashMap<>();
        if (!poIds.isEmpty()) {
            for (Object[] row : purchaseOrderLineRepository.sumPendingReceiptQtyByPurchaseOrderIds(poIds)) {
                pendingByPo.put((Long) row[0], (BigDecimal) row[1]);
            }
        }
        Set<Long> grIds = list.stream().map(GoodsReceipt::getId).collect(Collectors.toSet());
        Set<Long> withAsn = new HashSet<>();
        if (!grIds.isEmpty()) {
            withAsn.addAll(goodsReceiptLineRepository.findGoodsReceiptIdsHavingAsnLine(grIds));
        }
        Set<Long> poIdsWithSubmittedAsn = new HashSet<>();
        if (!poIds.isEmpty()) {
            poIdsWithSubmittedAsn.addAll(asnNoticeRepository.findPurchaseOrderIdsHavingSubmittedAsn(poIds));
        }
        Map<Long, String> asnSummaryByGrId = asnSummaryByGoodsReceiptId(grIds);
        return list.stream()
                .map(g -> GoodsReceiptSummaryResponse.from(
                        g,
                        pendingByPo.getOrDefault(g.getPurchaseOrder().getId(), BigDecimal.ZERO),
                        withAsn.contains(g.getId()),
                        poIdsWithSubmittedAsn.contains(g.getPurchaseOrder().getId()),
                        asnSummaryByGrId.getOrDefault(g.getId(), "")))
                .toList();
    }

    /** 每个收货单下去重后的 ASN 单号，逗号拼接（与列表「发货通知」列一致） */
    private Map<Long, String> asnSummaryByGoodsReceiptId(Set<Long> grIds) {
        if (grIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LinkedHashSet<String>> tmp = new LinkedHashMap<>();
        for (Object[] row : goodsReceiptLineRepository.findAsnNoRowsByGoodsReceiptIds(grIds)) {
            Long grId = (Long) row[0];
            String asnNo = (String) row[1];
            if (asnNo == null || asnNo.isBlank()) {
                continue;
            }
            tmp.computeIfAbsent(grId, k -> new LinkedHashSet<>()).add(asnNo.trim());
        }
        Map<Long, String> out = new LinkedHashMap<>();
        for (var e : tmp.entrySet()) {
            out.put(e.getKey(), String.join(", ", e.getValue()));
        }
        return out;
    }

    /**
     * 已有供应商提交的发货通知、但本采购组织下尚未创建任何收货单的订单（用于「待收货的发货通知」待建收货入口）。
     */
    @Transactional(readOnly = true)
    public List<OpenPoAsnReceiptRow> listOpenPurchaseOrdersWithSubmittedAsnNoGoodsReceipt(Long procurementOrgId) {
        List<PurchaseOrder> pos = purchaseOrderRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
        Set<Long> poWithAnyGr = goodsReceiptRepository.findDistinctPurchaseOrderIdsByProcurementOrgId(procurementOrgId);
        Set<Long> allReleasedPoIds = pos.stream()
                .filter(p -> p.getStatus() == PoStatus.RELEASED)
                .map(PurchaseOrder::getId)
                .collect(Collectors.toSet());
        if (allReleasedPoIds.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> pendingByPo = new HashMap<>();
        for (Object[] row : purchaseOrderLineRepository.sumPendingReceiptQtyByPurchaseOrderIds(allReleasedPoIds)) {
            pendingByPo.put((Long) row[0], (BigDecimal) row[1]);
        }
        Set<Long> withSubmittedAsn = new HashSet<>(asnNoticeRepository.findPurchaseOrderIdsHavingSubmittedAsn(allReleasedPoIds));
        List<OpenPoAsnReceiptRow> out = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            if (po.getStatus() != PoStatus.RELEASED) {
                continue;
            }
            long poId = po.getId();
            if (poWithAnyGr.contains(poId)) {
                continue;
            }
            if (!withSubmittedAsn.contains(poId)) {
                continue;
            }
            BigDecimal pend = pendingByPo.getOrDefault(poId, BigDecimal.ZERO);
            if (pend.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            AsnNotice asn = asnNoticeRepository
                    .findFirstByPurchaseOrder_IdAndStatusOrderByIdDesc(poId, AsnStatus.SUBMITTED)
                    .orElse(null);
            if (asn == null) {
                continue;
            }
            out.add(new OpenPoAsnReceiptRow(
                    poId,
                    po.getPoNo(),
                    asn.getAsnNo(),
                    pend.stripTrailingZeros().toPlainString()));
        }
        return out;
    }

    public record OpenPoAsnReceiptRow(
            long purchaseOrderId,
            String poNo,
            String asnNo,
            String pendingReceiptQty
    ) {}

    @Transactional(readOnly = true)
    public GoodsReceipt requireDetail(Long id) {
        return goodsReceiptRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("收货单不存在: " + id));
    }

    @Transactional
    public GoodsReceipt create(
            Long procurementOrgId,
            Long purchaseOrderId,
            Long warehouseId,
            LocalDate receiptDate,
            String remark,
            List<GrLineInput> lines
    ) {
        var org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + procurementOrgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        Warehouse wh = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("仓库不存在: " + warehouseId));
        if (!wh.getProcurementOrg().getId().equals(procurementOrgId)) {
            throw new BadRequestException("仓库不属于该采购组织");
        }

        PurchaseOrder po = purchaseOrderRepository.findWithDetailsById(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + purchaseOrderId));
        if (!po.getProcurementOrg().getId().equals(procurementOrgId)) {
            throw new BadRequestException("订单不属于该采购组织");
        }
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布订单可收货，当前: " + po.getStatus());
        }

        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("收货至少一行");
        }

        Map<Long, PurchaseOrderLine> polMap = po.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));

        BigDecimal ratio = BigDecimal.ONE.add(srmProperties.getOverReceiveRatio());

        for (GrLineInput in : lines) {
            PurchaseOrderLine pol = polMap.get(in.purchaseOrderLineId());
            if (pol == null) {
                throw new BadRequestException("订单行无效: " + in.purchaseOrderLineId());
            }
            if (in.receivedQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("收货数量须大于 0");
            }
            if (in.asnLineId() == null) {
                throw new BadRequestException("行 " + pol.getLineNo() + " 收货必须关联 ASN 发货通知");
            }
            BigDecimal maxRecv = pol.getQty().multiply(ratio).setScale(4, RoundingMode.HALF_UP)
                    .subtract(pol.getReceivedQty());
            if (in.receivedQty().compareTo(maxRecv) > 0) {
                throw new BadRequestException("行 " + pol.getLineNo() + " 超过可收上限（含超收比例）: " + maxRecv);
            }
            AsnLine al = asnLineRepository.findById(in.asnLineId())
                    .orElseThrow(() -> new NotFoundException("ASN 行不存在: " + in.asnLineId()));
            if (!al.getPurchaseOrderLine().getId().equals(pol.getId())) {
                throw new BadRequestException("ASN 行与订单行不匹配");
            }
        }

        String grNo = grNumberAllocator.nextGrNo(org);
        GoodsReceipt gr = new GoodsReceipt();
        gr.setGrNo(grNo);
        gr.setProcurementOrg(org);
        gr.setLedger(po.getLedger());
        gr.setSupplier(po.getSupplier());
        gr.setWarehouse(wh);
        gr.setPurchaseOrder(po);
        gr.setReceiptDate(receiptDate);
        gr.setRemark(remark);

        int n = 1;
        for (GrLineInput in : lines) {
            PurchaseOrderLine pol = polMap.get(in.purchaseOrderLineId());
            GoodsReceiptLine gl = new GoodsReceiptLine();
            gl.setGoodsReceipt(gr);
            gl.setPurchaseOrderLine(pol);
            gl.setAsnLine(asnLineRepository.findById(in.asnLineId())
                    .orElseThrow(() -> new NotFoundException("ASN 行不存在: " + in.asnLineId())));
            gl.setLineNo(n++);
            gl.setReceivedQty(in.receivedQty());
            gr.getLines().add(gl);

            pol.setReceivedQty(pol.getReceivedQty().add(in.receivedQty()));
            purchaseOrderLineRepository.save(pol);
        }

        GoodsReceipt saved = goodsReceiptRepository.save(gr);
        staffNotificationService.notifyProcurementOrgStakeholders(
                procurementOrgId,
                "收货单已登记",
                "收货单 " + saved.getGrNo() + " 已创建，关联订单 " + po.getPoNo() + "。",
                "GR_CREATED",
                "GR",
                saved.getId());

        boolean allReceived = po.getLines().stream()
                .allMatch(l -> l.getReceivedQty().compareTo(l.getQty()) >= 0);
        if (allReceived) {
            po.setStatus(PoStatus.CLOSED);
            purchaseOrderRepository.save(po);
        }

        return saved;
    }

    /**
     * 按采购订单补全历史收货行的 {@code asn_line_id}：与前端新建收货一致，对每条订单行取「发货通知 id 最大」的 ASN 行。
     * 仅当该订单行存在 ASN 时写入；无 ASN 映射的行跳过。{@code overwriteExisting=true} 时对订单下所有收货行重算 ASN（仍仅在有映射时覆盖）。
     */
    @Transactional
    public GrAsnBackfillResult backfillAsnLineIdsForPurchaseOrder(Long purchaseOrderId, boolean overwriteExisting) {
        PurchaseOrder po = purchaseOrderRepository.findWithDetailsById(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + purchaseOrderId));
        Map<Long, AsnLine> polToAsn = bestAsnLinePerPurchaseOrderLine(po);
        List<GoodsReceiptLine> lines = overwriteExisting
                ? goodsReceiptLineRepository.findForPurchaseOrder(purchaseOrderId)
                : goodsReceiptLineRepository.findForPurchaseOrderWithNullAsnLine(purchaseOrderId);
        int updated = 0;
        int skippedNoAsn = 0;
        for (GoodsReceiptLine gl : lines) {
            Long polId = gl.getPurchaseOrderLine().getId();
            AsnLine al = polToAsn.get(polId);
            if (al == null) {
                skippedNoAsn++;
                continue;
            }
            gl.setAsnLine(al);
            updated++;
        }
        if (updated > 0) {
            goodsReceiptLineRepository.saveAll(lines);
        }
        return new GrAsnBackfillResult(po.getId(), po.getPoNo(), lines.size(), updated, skippedNoAsn, overwriteExisting);
    }

    /**
     * 发货通知按 id 降序遍历，每条订单行首次出现的 ASN 行即为「最新通知」对应行（与 GrCreateView 一致）。
     */
    private Map<Long, AsnLine> bestAsnLinePerPurchaseOrderLine(PurchaseOrder po) {
        List<AsnNotice> notices = asnNoticeRepository.findByPurchaseOrderOrderByIdDesc(po);
        Map<Long, AsnLine> map = new LinkedHashMap<>();
        for (AsnNotice n : notices) {
            if (n.getStatus() != AsnStatus.SUBMITTED) {
                continue;
            }
            for (AsnLine line : n.getLines()) {
                map.putIfAbsent(line.getPurchaseOrderLine().getId(), line);
            }
        }
        return map;
    }

    public record GrLineInput(Long purchaseOrderLineId, BigDecimal receivedQty, Long asnLineId) {}

    public record GrAsnBackfillResult(
            long purchaseOrderId,
            String poNo,
            int receiptLinesConsidered,
            int linesUpdated,
            int linesSkippedNoAsnMapping,
            boolean overwriteExisting
    ) {}
}
