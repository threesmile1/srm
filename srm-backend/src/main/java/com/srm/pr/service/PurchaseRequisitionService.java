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
import org.springframework.util.StringUtils;

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
     * 供应商、单价、约定交期由采购在转单时填写；按所选供应商分组，每组生成一张 PO。
     * 约定交期未填时默认使用请购行上的需求交期。
     */
    @Transactional
    public List<PurchaseOrder> convertToPo(Long prId, List<ConvertPrLineCmd> commands) {
        PurchaseRequisition pr = requireDetail(prId);
        if (pr.getStatus() != PrStatus.APPROVED && pr.getStatus() != PrStatus.PARTIALLY_CONVERTED) {
            throw new BadRequestException("仅已批准或部分转单的请购单可转PO，当前状态: " + pr.getStatus());
        }

        if (commands == null || commands.isEmpty()) {
            throw new BadRequestException("请提供转单行及采购信息");
        }
        long distinctLineIds = commands.stream().map(ConvertPrLineCmd::lineId).distinct().count();
        if (distinctLineIds != commands.size()) {
            throw new BadRequestException("转单行不能重复");
        }

        Map<Long, PurchaseRequisitionLine> lineById = pr.getLines().stream()
                .collect(Collectors.toMap(PurchaseRequisitionLine::getId, l -> l, (a, b) -> a, LinkedHashMap::new));

        for (ConvertPrLineCmd cmd : commands) {
            PurchaseRequisitionLine l = lineById.get(cmd.lineId());
            if (l == null) {
                throw new BadRequestException("请购单不包含行 id: " + cmd.lineId());
            }
            if (l.getConvertedPo() != null) {
                throw new BadRequestException("行 " + l.getLineNo() + " 已转单");
            }
            if (cmd.supplierId() == null) {
                throw new BadRequestException("行 " + l.getLineNo() + " 请选择供应商");
            }
            if (cmd.unitPrice() == null || cmd.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("行 " + l.getLineNo() + " 请填写有效采购单价");
            }
            Supplier supplier = masterDataService.requireSupplier(cmd.supplierId());
            masterDataService.assertSupplierAuthorizedForOrg(supplier, pr.getProcurementOrg());
            masterDataService.assertSupplierAllowedForPurchaseOrder(supplier);
        }

        Map<Long, List<ConvertPrLineCmd>> bySupplier = commands.stream()
                .collect(Collectors.groupingBy(ConvertPrLineCmd::supplierId, LinkedHashMap::new, Collectors.toList()));

        List<PurchaseOrder> createdPos = new ArrayList<>();
        for (var entry : bySupplier.entrySet()) {
            Long supplierId = entry.getKey();
            List<ConvertPrLineCmd> groupCmds = entry.getValue();
            List<CreateLine> poLines = new ArrayList<>();
            for (ConvertPrLineCmd cmd : groupCmds) {
                PurchaseRequisitionLine l = lineById.get(cmd.lineId());
                java.time.LocalDate poDate = cmd.requestedDate() != null ? cmd.requestedDate() : l.getRequestedDate();
                Warehouse wh = l.getWarehouse() != null ? l.getWarehouse() : resolveWarehouseOrNull(pr.getProcurementOrg(), l.getMaterial());
                if (wh == null) {
                    throw new BadRequestException("行 " + l.getLineNo() + " 未指定仓库（可不填），但系统也无法从物料默认仓解析；请维护物料四厂仓/仓库主档，或在请购行选择仓库");
                }
                poLines.add(new CreateLine(
                        l.getMaterial().getId(),
                        wh.getId(),
                        l.getQty(),
                        l.getUom(),
                        cmd.unitPrice(),
                        poDate));
            }

            PurchaseOrder po = purchaseOrderService.create(
                    pr.getProcurementOrg().getId(), supplierId,
                    "CNY", "由请购单 " + pr.getPrNo() + " 转换", poLines);

            for (ConvertPrLineCmd cmd : groupCmds) {
                lineById.get(cmd.lineId()).setConvertedPo(po);
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

    public record ConvertPrLineCmd(
            Long lineId,
            Long supplierId,
            BigDecimal unitPrice,
            java.time.LocalDate requestedDate
    ) {}

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

    /**
     * PR 转 PO 时仓库允许为空；若为空，则尝试按「采购组织维度」从物料四厂默认仓编码解析本地 warehouse 主档。
     * 解析失败返回 null，由调用方决定是否报错或补录。
     */
    private Warehouse resolveWarehouseOrNull(OrgUnit procurementOrg, MaterialItem material) {
        if (procurementOrg == null || material == null) {
            return null;
        }
        String orgName = procurementOrg.getName() != null ? procurementOrg.getName().trim() : "";
        String code = switch (orgName) {
            case "苏州工厂" -> material.getU9WarehouseSuzhou();
            case "成都工厂" -> material.getU9WarehouseChengdu();
            case "华南工厂" -> material.getU9WarehouseHuanan();
            case "水漆工厂" -> material.getU9WarehouseShuiqi();
            default -> null;
        };
        if (code == null || !StringUtils.hasText(code)) {
            return null;
        }
        return warehouseRepository.findByProcurementOrgAndCode(procurementOrg, code.trim()).orElse(null);
    }
}
