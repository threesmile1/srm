package com.srm.po.service;

import com.srm.approval.domain.ApprovalInstance;
import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.service.ApprovalService;
import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.foundation.service.AuditService;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.ForbiddenException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final MasterDataService masterDataService;
    private final PoNumberService poNumberService;
    private final AuditService auditService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    @Transactional(readOnly = true)
    public List<PurchaseOrder> listByOrg(Long procurementOrgId) {
        return purchaseOrderRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder requireDetail(Long id) {
        return purchaseOrderRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + id));
    }

    @Transactional(readOnly = true)
    public boolean existsU9Document(Long procurementOrgId, String u9DocNo) {
        if (!StringUtils.hasText(u9DocNo)) {
            return false;
        }
        return purchaseOrderRepository.existsByProcurementOrg_IdAndU9DocNo(procurementOrgId, u9DocNo.trim());
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
        masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);

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

        appendCreateLines(po, lines);

        if (po.getLines().isEmpty()) {
            throw new BadRequestException("订单至少一行");
        }

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditService.log(null, null, "CREATE_PO", "PO", saved.getId(),
                "poNo=" + saved.getPoNo() + " lines=" + saved.getLines().size(), null);
        return saved;
    }

    /**
     * U9 帆软已审采购订单同步：按采购组织 + U9 单号幂等；新建或覆盖（无收货）后自动 {@link PoStatus#RELEASED} 并通知供应商。
     */
    @Transactional
    public PurchaseOrder upsertFromU9AndRelease(
            Long procurementOrgId,
            String u9DocNo,
            Long supplierId,
            String currency,
            String remark,
            LocalDate businessDate,
            String officialOrderNo,
            String store2,
            String receiverName,
            String terminalPhone,
            String installAddress,
            List<CreateLine> lines) {
        if (!StringUtils.hasText(u9DocNo)) {
            throw new BadRequestException("U9 单据编号不能为空");
        }
        String u9Key = u9DocNo.trim();
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + procurementOrgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        masterDataService.assertSupplierAuthorizedForOrg(supplier, org);
        masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);
        Ledger ledger = org.getLedger();
        if (ledger == null) {
            throw new BadRequestException("采购组织未关联账套");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("订单至少一行");
        }

        Optional<PurchaseOrder> existingOpt =
                purchaseOrderRepository.findByProcurementOrg_IdAndU9DocNo(org.getId(), u9Key);
        if (existingOpt.isPresent()) {
            return refreshU9PurchaseOrderLinesAndRelease(existingOpt.get(), supplier, currency, remark,
                    businessDate, officialOrderNo, store2, receiverName, terminalPhone, installAddress, lines);
        }

        String poNo = poNumberService.nextPoNo(org);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(poNo);
        po.setProcurementOrg(org);
        po.setLedger(ledger);
        po.setSupplier(supplier);
        po.setCurrency(currency != null && !currency.isBlank() ? currency : "CNY");
        po.setU9DocNo(u9Key);
        po.setRemark(remark);
        po.setU9BusinessDate(businessDate);
        po.setU9OfficialOrderNo(officialOrderNo);
        po.setU9Store2(store2);
        po.setU9ReceiverName(receiverName);
        po.setU9TerminalPhone(terminalPhone);
        po.setU9InstallAddress(installAddress);
        po.setRevisionNo(1);
        po.setStatus(PoStatus.DRAFT);

        appendCreateLines(po, lines);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        saved.setStatus(PoStatus.APPROVED);
        saved = purchaseOrderRepository.save(saved);
        auditService.log(null, null, "CREATE_PO_U9", "PO", saved.getId(),
                "poNo=" + saved.getPoNo() + " u9DocNo=" + u9Key + " lines=" + saved.getLines().size(), null);
        return release(saved.getId());
    }

    private PurchaseOrder refreshU9PurchaseOrderLinesAndRelease(
            PurchaseOrder po,
            Supplier supplier,
            String currency,
            String remark,
            LocalDate businessDate,
            String officialOrderNo,
            String store2,
            String receiverName,
            String terminalPhone,
            String installAddress,
            List<CreateLine> lines) {
        if (po.getStatus() == PoStatus.CANCELLED || po.getStatus() == PoStatus.CLOSED) {
            throw new BadRequestException("订单已关闭或取消，不可覆盖同步: " + po.getPoNo());
        }
        if (po.getStatus() == PoStatus.PENDING_APPROVAL || po.getStatus() == PoStatus.DRAFT) {
            throw new BadRequestException("订单状态异常(U9同步)，请人工处理: " + po.getPoNo() + " " + po.getStatus());
        }
        for (PurchaseOrderLine old : po.getLines()) {
            if (old.getReceivedQty() != null && old.getReceivedQty().compareTo(BigDecimal.ZERO) > 0) {
                throw new BadRequestException("订单已有收货，禁止整单覆盖: " + po.getPoNo());
            }
        }
        if (!po.getSupplier().getId().equals(supplier.getId())) {
            masterDataService.assertSupplierAuthorizedForOrg(supplier, po.getProcurementOrg());
            masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);
            po.setSupplier(supplier);
        }
        if (StringUtils.hasText(currency)) {
            po.setCurrency(currency.trim());
        }
        po.setRemark(remark);
        po.setU9BusinessDate(businessDate);
        po.setU9OfficialOrderNo(officialOrderNo);
        po.setU9Store2(store2);
        po.setU9ReceiverName(receiverName);
        po.setU9TerminalPhone(terminalPhone);
        po.setU9InstallAddress(installAddress);
        po.getLines().clear();
        appendCreateLines(po, lines);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditService.log(null, null, "UPDATE_PO_U9", "PO", saved.getId(),
                "poNo=" + saved.getPoNo() + " lines=" + saved.getLines().size(), null);
        if (saved.getStatus() == PoStatus.APPROVED) {
            return release(saved.getId());
        }
        return saved;
    }

    private void appendCreateLines(PurchaseOrder po, List<CreateLine> lines) {
        OrgUnit org = po.getProcurementOrg();
        int n = po.getLines().stream().mapToInt(PurchaseOrderLine::getLineNo).max().orElse(0) + 1;
        for (CreateLine cl : lines) {
            MaterialItem mat = masterDataService.requireMaterial(cl.materialId());
            Warehouse wh = warehouseRepository.findById(cl.warehouseId())
                    .orElseThrow(() -> new NotFoundException("仓库不存在: " + cl.warehouseId()));
            if (!wh.getProcurementOrg().getId().equals(org.getId())) {
                throw new BadRequestException("仓库不属于当前采购组织: " + wh.getCode());
            }
            BigDecimal qty = cl.qty();
            BigDecimal price = cl.unitPrice();
            BigDecimal amount = cl.amountOverride() != null
                    ? cl.amountOverride().setScale(4, RoundingMode.HALF_UP)
                    : qty.multiply(price).setScale(4, RoundingMode.HALF_UP);

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
    }

    @Transactional
    public PurchaseOrder submitForApproval(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.DRAFT) {
            throw new BadRequestException("仅草稿可提交审批，当前状态: " + po.getStatus());
        }

        BigDecimal totalAmount = po.getLines().stream()
                .map(PurchaseOrderLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            approvalService.startApproval("PO", po.getId(), po.getPoNo(), totalAmount);
            po.setStatus(PoStatus.PENDING_APPROVAL);
        } catch (Exception e) {
            log.warn("PO审批流程启动失败(无匹配规则则自动审批): {}", e.getMessage());
            po.setStatus(PoStatus.APPROVED);
        }
        auditService.log(null, null, "SUBMIT_PO", "PO", id, "poNo=" + po.getPoNo(), null);
        return purchaseOrderRepository.save(po);
    }

    /**
     * 已不再支持从单据页「一键审核」：请使用 {@link #submitForApproval(Long)} 发起审批，
     * 在审批中心处理；引擎回调会写回状态。保留方法仅为避免误调用时给出明确错误。
     */
    @Transactional
    public PurchaseOrder approve(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() == PoStatus.DRAFT) {
            throw new BadRequestException("请使用「提交审批」发起流程；无匹配审批规则时将自动通过。");
        }
        if (po.getStatus() == PoStatus.PENDING_APPROVAL) {
            ApprovalInstance inst = approvalService.getInstanceByDoc("PO", id);
            if (inst != null && inst.getStatus() == ApprovalStatus.PENDING) {
                throw new BadRequestException("订单已进入审批流程，请在「审批中心」处理。");
            }
            throw new BadRequestException("订单状态异常，请刷新后重试或联系管理员。");
        }
        throw new BadRequestException("当前状态不可执行该操作: " + po.getStatus());
    }

    @Transactional
    public PurchaseOrder release(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.APPROVED) {
            throw new BadRequestException("仅已审核订单可发布，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.RELEASED);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        auditService.log(null, null, "RELEASE_PO", "PO", id, "poNo=" + po.getPoNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "新采购订单已发布",
                    "订单号 " + saved.getPoNo() + " 已发布，请在门户确认交期与数量。",
                    "PO_RELEASED",
                    "PO",
                    saved.getId());
        } catch (Exception e) {
            log.warn("PO 发布后写入供应商通知失败: {}", e.getMessage());
        }
        return saved;
    }

    @Transactional
    public PurchaseOrder cancel(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.DRAFT && po.getStatus() != PoStatus.APPROVED) {
            throw new BadRequestException("仅草稿或已审核可取消，当前状态: " + po.getStatus());
        }
        po.setStatus(PoStatus.CANCELLED);
        auditService.log(null, null, "CANCEL_PO", "PO", id, "poNo=" + po.getPoNo(), null);
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder close(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布可关闭，当前状态: " + po.getStatus());
        }
        boolean anyOpen = po.getLines().stream().anyMatch(l -> {
            BigDecimal qty = l.getQty() != null ? l.getQty() : BigDecimal.ZERO;
            BigDecimal received = l.getReceivedQty() != null ? l.getReceivedQty() : BigDecimal.ZERO;
            return qty.compareTo(received) > 0;
        });
        if (anyOpen) {
            throw new BadRequestException("订单未全部收货，禁止手工关闭；请完成收货后由系统自动关闭或联系管理员处理。");
        }
        po.setStatus(PoStatus.CLOSED);
        auditService.log(null, null, "CLOSE_PO", "PO", id, "poNo=" + po.getPoNo(), null);
        return purchaseOrderRepository.save(po);
    }

    /**
     * 误点关闭兜底：无收货时允许从 CLOSED 恢复到 RELEASED。
     */
    @Transactional
    public PurchaseOrder reopenIfNoReceipt(Long id) {
        PurchaseOrder po = requireDetail(id);
        if (po.getStatus() != PoStatus.CLOSED) {
            throw new BadRequestException("仅已关闭订单可恢复，当前状态: " + po.getStatus());
        }
        boolean anyReceived = po.getLines().stream().anyMatch(l -> {
            BigDecimal received = l.getReceivedQty() != null ? l.getReceivedQty() : BigDecimal.ZERO;
            return received.compareTo(BigDecimal.ZERO) > 0;
        });
        if (anyReceived) {
            throw new BadRequestException("订单已有收货记录，禁止恢复关闭状态，请联系管理员走受控流程处理。");
        }
        po.setStatus(PoStatus.RELEASED);
        auditService.log(null, null, "REOPEN_PO", "PO", id, "poNo=" + po.getPoNo(), null);
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
            throw new ForbiddenException("无权确认该行");
        }
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布订单可确认行，当前状态: " + po.getStatus());
        }
        line.setConfirmedQty(confirmedQty);
        line.setPromisedDate(promisedDate);
        line.setSupplierRemark(supplierRemark);
        line.setConfirmedAt(Instant.now());
        PurchaseOrderLine saved = purchaseOrderLineRepository.save(line);
        staffNotificationService.notifyProcurementOrgStakeholders(
                po.getProcurementOrg().getId(),
                "订单行已确认",
                "订单 " + po.getPoNo() + " 第 " + saved.getLineNo() + " 行已由供应商确认交期与数量。",
                "PO_LINE_CONFIRMED",
                "PO",
                po.getId());
        return saved;
    }

    public record CreateLine(
            Long materialId,
            Long warehouseId,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            LocalDate requestedDate,
            /** 非空时写入行金额（如 U9 价税合计），否则为 qty×unitPrice */
            BigDecimal amountOverride
    ) {
        public CreateLine(
                Long materialId,
                Long warehouseId,
                BigDecimal qty,
                String uom,
                BigDecimal unitPrice,
                LocalDate requestedDate) {
            this(materialId, warehouseId, qty, uom, unitPrice, requestedDate, null);
        }
    }
}
