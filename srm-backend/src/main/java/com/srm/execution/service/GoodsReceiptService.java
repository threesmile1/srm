package com.srm.execution.service;

import com.srm.config.SrmProperties;
import com.srm.execution.domain.AsnLine;
import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.GoodsReceiptLine;
import com.srm.execution.repo.AsnLineRepository;
import com.srm.execution.repo.GoodsReceiptRepository;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final AsnLineRepository asnLineRepository;
    private final GrNumberAllocator grNumberAllocator;
    private final SrmProperties srmProperties;
    private final StaffNotificationService staffNotificationService;

    @Transactional(readOnly = true)
    public List<GoodsReceipt> listByOrg(Long procurementOrgId) {
        return goodsReceiptRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
    }

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
            BigDecimal maxRecv = pol.getQty().multiply(ratio).setScale(4, RoundingMode.HALF_UP)
                    .subtract(pol.getReceivedQty());
            if (in.receivedQty().compareTo(maxRecv) > 0) {
                throw new BadRequestException("行 " + pol.getLineNo() + " 超过可收上限（含超收比例）: " + maxRecv);
            }
            if (in.asnLineId() != null) {
                AsnLine al = asnLineRepository.findById(in.asnLineId())
                        .orElseThrow(() -> new NotFoundException("ASN 行不存在: " + in.asnLineId()));
                if (!al.getPurchaseOrderLine().getId().equals(pol.getId())) {
                    throw new BadRequestException("ASN 行与订单行不匹配");
                }
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
            if (in.asnLineId() != null) {
                gl.setAsnLine(asnLineRepository.findById(in.asnLineId()).orElse(null));
            }
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

    public record GrLineInput(Long purchaseOrderLineId, BigDecimal receivedQty, Long asnLineId) {}
}
