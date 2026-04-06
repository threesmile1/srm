package com.srm.pr.service;

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
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.service.PurchaseOrderService;
import com.srm.po.service.PurchaseOrderService.CreateLine;
import com.srm.pr.domain.PrStatus;
import com.srm.pr.domain.PurchaseRequisition;
import com.srm.pr.domain.PurchaseRequisitionLine;
import com.srm.pr.repo.PurchaseRequisitionRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRequisitionService {

    private final PurchaseRequisitionRepository prRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final MasterDataService masterDataService;
    private final PrNumberService prNumberService;
    private final PurchaseOrderService purchaseOrderService;
    private final AuditService auditService;
    private final ApprovalService approvalService;

    @Transactional(readOnly = true)
    public List<PurchaseRequisition> listByOrg(Long procurementOrgId) {
        return prRepository.findByProcurementOrgIdOrderByIdDesc(procurementOrgId);
    }

    @Transactional(readOnly = true)
    public PurchaseRequisition requireDetail(Long id) {
        return prRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("请购单不存在: " + id));
    }

    @Transactional
    public PurchaseRequisition create(Long procurementOrgId, String requesterName, String department,
                                       String remark, List<CreatePrLine> lines) {
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + procurementOrgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }

        Ledger ledger = org.getLedger();
        if (ledger == null) throw new BadRequestException("采购组织未关联账套");

        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNo(prNumberService.nextPrNo(org));
        pr.setProcurementOrg(org);
        pr.setLedger(ledger);
        pr.setRequesterName(requesterName);
        pr.setDepartment(department);
        pr.setStatus(PrStatus.DRAFT);
        pr.setRemark(remark);

        int n = 1;
        for (CreatePrLine cl : lines) {
            MaterialItem mat = masterDataService.requireMaterial(cl.materialId());
            PurchaseRequisitionLine line = new PurchaseRequisitionLine();
            line.setPurchaseRequisition(pr);
            line.setLineNo(n++);
            line.setMaterial(mat);
            line.setQty(cl.qty());
            line.setUom(cl.uom() != null && !cl.uom().isBlank() ? cl.uom() : mat.getUom());
            line.setUnitPrice(cl.unitPrice());
            line.setRequestedDate(cl.requestedDate());
            if (cl.warehouseId() != null) {
                Warehouse wh = warehouseRepository.findById(cl.warehouseId())
                        .orElseThrow(() -> new NotFoundException("仓库不存在: " + cl.warehouseId()));
                line.setWarehouse(wh);
            }
            if (cl.supplierId() != null) {
                Supplier sup = masterDataService.requireSupplier(cl.supplierId());
                line.setSupplier(sup);
            }
            line.setRemark(cl.remark());
            pr.getLines().add(line);
        }

        if (pr.getLines().isEmpty()) throw new BadRequestException("请购单至少一行");

        PurchaseRequisition saved = prRepository.save(pr);
        auditService.log(null, null, "CREATE_PR", "PR", saved.getId(),
                "prNo=" + saved.getPrNo(), null);
        return saved;
    }

    @Transactional
    public PurchaseRequisition submit(Long id) {
        PurchaseRequisition pr = requireDetail(id);
        if (pr.getStatus() != PrStatus.DRAFT) {
            throw new BadRequestException("仅草稿可提交，当前状态: " + pr.getStatus());
        }
        pr.setStatus(PrStatus.PENDING_APPROVAL);
        prRepository.save(pr);

        BigDecimal totalAmount = pr.getLines().stream()
                .map(l -> l.getUnitPrice() != null && l.getQty() != null
                        ? l.getUnitPrice().multiply(l.getQty()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            approvalService.startApproval("PR", pr.getId(), pr.getPrNo(), totalAmount);
        } catch (Exception e) {
            log.warn("PR审批流程启动失败(无匹配规则则自动审批): {}", e.getMessage());
            pr.setStatus(PrStatus.APPROVED);
            prRepository.save(pr);
        }

        auditService.log(null, null, "SUBMIT_PR", "PR", id, "prNo=" + pr.getPrNo(), null);
        return pr;
    }

    @Transactional
    public PurchaseRequisition approve(Long id) {
        PurchaseRequisition pr = requireDetail(id);
        if (pr.getStatus() != PrStatus.PENDING_APPROVAL) {
            throw new BadRequestException("仅待审批可审批，当前状态: " + pr.getStatus());
        }
        assertNoPendingApprovalWorkflowOrThrow("PR", id);
        pr.setStatus(PrStatus.APPROVED);
        auditService.log(null, null, "APPROVE_PR", "PR", id, "prNo=" + pr.getPrNo(), null);
        return prRepository.save(pr);
    }

    @Transactional
    public PurchaseRequisition reject(Long id, String reason) {
        PurchaseRequisition pr = requireDetail(id);
        if (pr.getStatus() != PrStatus.PENDING_APPROVAL) {
            throw new BadRequestException("仅待审批可驳回，当前状态: " + pr.getStatus());
        }
        assertNoPendingApprovalWorkflowOrThrow("PR", id);
        pr.setStatus(PrStatus.REJECTED);
        auditService.log(null, null, "REJECT_PR", "PR", id,
                "prNo=" + pr.getPrNo() + " reason=" + reason, null);
        return prRepository.save(pr);
    }

    /** 已存在引擎待办时，必须通过审批中心操作，避免与 {@link com.srm.pr.service.PrApprovalCallback} 双写。 */
    private void assertNoPendingApprovalWorkflowOrThrow(String docType, Long docId) {
        ApprovalInstance inst = approvalService.getInstanceByDoc(docType, docId);
        if (inst != null && inst.getStatus() == ApprovalStatus.PENDING) {
            throw new BadRequestException("该单据已关联审批流程，请在「审批中心」审批或驳回。");
        }
    }

    @Transactional
    public PurchaseRequisition cancel(Long id) {
        PurchaseRequisition pr = requireDetail(id);
        if (pr.getStatus() != PrStatus.DRAFT && pr.getStatus() != PrStatus.APPROVED) {
            throw new BadRequestException("仅草稿或已批准可取消，当前状态: " + pr.getStatus());
        }
        pr.setStatus(PrStatus.CANCELLED);
        auditService.log(null, null, "CANCEL_PR", "PR", id, "prNo=" + pr.getPrNo(), null);
        return prRepository.save(pr);
    }

    /**
     * Convert approved PR lines to Purchase Orders.
     * Groups selected lines by supplier; each group becomes one PO.
     */
    @Transactional
    public List<PurchaseOrder> convertToPo(Long prId, List<Long> lineIds) {
        PurchaseRequisition pr = requireDetail(prId);
        if (pr.getStatus() != PrStatus.APPROVED && pr.getStatus() != PrStatus.PARTIALLY_CONVERTED) {
            throw new BadRequestException("仅已批准或部分转单的请购单可转PO，当前状态: " + pr.getStatus());
        }

        List<PurchaseRequisitionLine> selectedLines = pr.getLines().stream()
                .filter(l -> lineIds.contains(l.getId()))
                .toList();

        if (selectedLines.isEmpty()) throw new BadRequestException("请选择要转单的行");

        for (PurchaseRequisitionLine l : selectedLines) {
            if (l.getConvertedPo() != null) {
                throw new BadRequestException("行 " + l.getLineNo() + " 已转单");
            }
            if (l.getSupplier() == null) {
                throw new BadRequestException("行 " + l.getLineNo() + " 未指定供应商");
            }
            if (l.getWarehouse() == null) {
                throw new BadRequestException("行 " + l.getLineNo() + " 未指定仓库");
            }
            if (l.getUnitPrice() == null || l.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("行 " + l.getLineNo() + " 未设定单价");
            }
        }

        Map<Long, List<PurchaseRequisitionLine>> bySupplier = selectedLines.stream()
                .collect(Collectors.groupingBy(l -> l.getSupplier().getId(), LinkedHashMap::new, Collectors.toList()));

        List<PurchaseOrder> createdPos = new ArrayList<>();
        for (var entry : bySupplier.entrySet()) {
            Long supplierId = entry.getKey();
            List<PurchaseRequisitionLine> group = entry.getValue();
            List<CreateLine> poLines = group.stream()
                    .map(l -> new CreateLine(
                            l.getMaterial().getId(),
                            l.getWarehouse().getId(),
                            l.getQty(),
                            l.getUom(),
                            l.getUnitPrice(),
                            l.getRequestedDate()))
                    .toList();

            PurchaseOrder po = purchaseOrderService.create(
                    pr.getProcurementOrg().getId(), supplierId,
                    "CNY", "由请购单 " + pr.getPrNo() + " 转换", poLines);

            for (PurchaseRequisitionLine l : group) {
                l.setConvertedPo(po);
            }
            createdPos.add(po);
        }

        boolean allConverted = pr.getLines().stream().allMatch(l -> l.getConvertedPo() != null);
        pr.setStatus(allConverted ? PrStatus.FULLY_CONVERTED : PrStatus.PARTIALLY_CONVERTED);
        prRepository.save(pr);

        auditService.log(null, null, "CONVERT_PR_TO_PO", "PR", prId,
                "prNo=" + pr.getPrNo() + " pos=" + createdPos.size(), null);
        return createdPos;
    }

    public record CreatePrLine(
            Long materialId,
            Long warehouseId,
            Long supplierId,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            java.time.LocalDate requestedDate,
            String remark
    ) {}
}
