package com.srm.po.service;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final MasterDataService masterDataService;
    private final PoNumberService poNumberService;

    @Transactional(readOnly = true)
    public List<PurchaseOrder> listByOrg(Long procurementOrgId) {
        return purchaseOrderRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder requireDetail(Long id) {
        return purchaseOrderRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + id));
    }

    @Transactional
    public PurchaseOrder create(Long procurementOrgId, Long supplierId, String currency, String remark,
                                  List<CreateLine> lines) {
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + procurementOrgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        masterDataService.assertSupplierAuthorizedForOrg(supplier, org);

        Ledger ledger = org.getLedger();
        if (ledger == null) {
            throw new BadRequestException("采购组织未关联账套");
        }

        String poNo = poNumberService.nextPoNo(org);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(poNo);
        po.setProcurementOrg(org);
        po.setLedger(ledger);
        po.setSupplier(supplier);
        po.setCurrency(currency != null && !currency.isBlank() ? currency : "CNY");
        po.setStatus(PoStatus.DRAFT);
        po.setRevisionNo(1);
        po.setRemark(remark);

        int n = 1;
        for (CreateLine cl : lines) {
            MaterialItem mat = masterDataService.requireMaterial(cl.materialId());
            Warehouse wh = warehouseRepository.findById(cl.warehouseId())
                    .orElseThrow(() -> new NotFoundException("仓库不存在: " + cl.warehouseId()));
            if (!wh.getProcurementOrg().getId().equals(org.getId())) {
                throw new BadRequestException("仓库不属于当前采购组织: " + wh.getCode());
            }
            BigDecimal qty = cl.qty();
            BigDecimal price = cl.unitPrice();
            BigDecimal amount = qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrder(po);
            line.setLineNo(n++);
            line.setMaterial(mat);
            line.setQty(qty);
            line.setUom(cl.uom() != null && !cl.uom().isBlank() ? cl.uom() : mat.getUom());
            line.setUnitPrice(price);
            line.setAmount(amount);
            line.setRequestedDate(cl.requestedDate());
            line.setWarehouse(wh);
            po.getLines().add(line);
        }

        if (po.getLines().isEmpty()) {
            throw new BadRequestException("订单至少一行");
        }

        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approve(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.DRAFT) {
            throw new BadRequestException("仅草稿可提交审核通过，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.APPROVED);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder release(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.APPROVED) {
            throw new BadRequestException("仅已审核订单可发布，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.RELEASED);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder cancel(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.DRAFT && po.getStatus() != PoStatus.APPROVED) {
            throw new BadRequestException("仅草稿或已审核可取消，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.CANCELLED);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder close(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布可关闭，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.CLOSED);
        return purchaseOrderRepository.save(po);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> listReleasedForSupplier(Long supplierId) {
        Supplier s = masterDataService.requireSupplier(supplierId);
        return purchaseOrderRepository.findBySupplierAndStatusOrderByIdDesc(s, PoStatus.RELEASED);
    }

    @Transactional
    public PurchaseOrderLine confirmLine(Long supplierId, Long lineId, BigDecimal confirmedQty,
                                         LocalDate promisedDate, String supplierRemark) {
        PurchaseOrderLine line = purchaseOrderLineRepository.findWithPoById(lineId)
                .orElseThrow(() -> new NotFoundException("订单行不存在: " + lineId));
        PurchaseOrder po = line.getPurchaseOrder();
        if (!po.getSupplier().getId().equals(supplierId)) {
            throw new BadRequestException("无权确认该行");
        }
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布订单可确认行，当前状态: " + po.getStatus());
        }
        line.setConfirmedQty(confirmedQty);
        line.setPromisedDate(promisedDate);
        line.setSupplierRemark(supplierRemark);
        line.setConfirmedAt(Instant.now());
        return purchaseOrderLineRepository.save(line);
    }

    public record CreateLine(
            Long materialId,
            Long warehouseId,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            LocalDate requestedDate
    ) {}
}
