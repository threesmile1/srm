package com.srm.quality.service;

import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.quality.domain.CorrectiveAction;
import com.srm.quality.domain.QualityInspection;
import com.srm.quality.repo.CorrectiveActionRepository;
import com.srm.quality.repo.QualityInspectionRepository;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {

    private final QualityInspectionRepository inspectionRepository;
    private final CorrectiveActionRepository correctiveActionRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final MasterDataService masterDataService;
    private final OrgUnitRepository orgUnitRepository;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    @Transactional(readOnly = true)
    public List<QualityInspection> listInspections(Long orgId) {
        return inspectionRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional
    public QualityInspection createInspection(Long grId, LocalDate inspectionDate,
                                               String inspectorName, String result,
                                               BigDecimal totalQty, BigDecimal qualifiedQty,
                                               BigDecimal defectQty, String defectType,
                                               String remark) {
        GoodsReceipt gr = goodsReceiptRepository.findById(grId)
                .orElseThrow(() -> new NotFoundException("收货单不存在: " + grId));

        QualityInspection qi = new QualityInspection();
        qi.setInspectionNo(generateInspectionNo());
        qi.setGoodsReceipt(gr);
        qi.setSupplier(gr.getSupplier());
        qi.setProcurementOrg(gr.getProcurementOrg());
        qi.setInspectionDate(inspectionDate);
        qi.setInspectorName(inspectorName);
        qi.setResult(result);
        qi.setTotalQty(totalQty);
        qi.setQualifiedQty(qualifiedQty != null ? qualifiedQty : BigDecimal.ZERO);
        qi.setDefectQty(defectQty != null ? defectQty : BigDecimal.ZERO);
        qi.setDefectType(defectType);
        qi.setRemark(remark);
        QualityInspection saved = inspectionRepository.save(qi);
        String content = String.format(
                "质检单 %s 已登记，结果 %s，关联收货单 %s。",
                saved.getInspectionNo(),
                saved.getResult(),
                gr.getGrNo());
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "质检结果已登记",
                    content,
                    "QUALITY_INSPECTION_RECORDED",
                    "QUALITY_INSPECTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("质检通知（供应商）失败: {}", e.getMessage());
        }
        try {
            staffNotificationService.notifyProcurementOrgStakeholders(
                    saved.getProcurementOrg().getId(),
                    "质检结果已登记",
                    content,
                    "QUALITY_INSPECTION_RECORDED",
                    "QUALITY_INSPECTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("质检通知（内部）失败: {}", e.getMessage());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CorrectiveAction> listCorrectiveActions(Long orgId) {
        return correctiveActionRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional
    public CorrectiveAction createCorrectiveAction(Long inspectionId, Long supplierId,
                                                    Long orgId, String issueDescription,
                                                    String rootCause, String correctiveMeasures,
                                                    LocalDate dueDate, String remark) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        OrgUnit org = orgUnitRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("组织不存在: " + orgId));

        CorrectiveAction ca = new CorrectiveAction();
        ca.setCaNo(generateCaNo());
        ca.setSupplier(supplier);
        ca.setProcurementOrg(org);
        ca.setIssueDescription(issueDescription);
        ca.setRootCause(rootCause);
        ca.setCorrectiveMeasures(correctiveMeasures);
        ca.setDueDate(dueDate);
        ca.setRemark(remark);

        if (inspectionId != null) {
            QualityInspection inspection = inspectionRepository.findById(inspectionId)
                    .orElseThrow(() -> new NotFoundException("质检单不存在: " + inspectionId));
            ca.setInspection(inspection);
        }

        CorrectiveAction saved = correctiveActionRepository.save(ca);
        String content = "纠正措施 " + saved.getCaNo() + " 已下达：" + saved.getIssueDescription();
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "纠正措施待处理",
                    content,
                    "CORRECTIVE_ACTION_ISSUED",
                    "CORRECTIVE_ACTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("纠正措施通知（供应商）失败: {}", e.getMessage());
        }
        try {
            staffNotificationService.notifyProcurementOrgStakeholders(
                    saved.getProcurementOrg().getId(),
                    "纠正措施已创建",
                    content,
                    "CORRECTIVE_ACTION_ISSUED",
                    "CORRECTIVE_ACTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("纠正措施通知（内部）失败: {}", e.getMessage());
        }
        return saved;
    }

    @Transactional
    public CorrectiveAction closeCorrectiveAction(Long id) {
        CorrectiveAction ca = correctiveActionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("纠正措施不存在: " + id));
        ca.setStatus("CLOSED");
        ca.setClosedDate(LocalDate.now());
        CorrectiveAction saved = correctiveActionRepository.save(ca);
        String content = "纠正措施 " + saved.getCaNo() + " 已关闭。";
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "纠正措施已关闭",
                    content,
                    "CORRECTIVE_ACTION_CLOSED",
                    "CORRECTIVE_ACTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("纠正措施关闭通知（供应商）失败: {}", e.getMessage());
        }
        try {
            staffNotificationService.notifyProcurementOrgStakeholders(
                    saved.getProcurementOrg().getId(),
                    "纠正措施已关闭",
                    content,
                    "CORRECTIVE_ACTION_CLOSED",
                    "CORRECTIVE_ACTION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("纠正措施关闭通知（内部）失败: {}", e.getMessage());
        }
        return saved;
    }

    private String generateInspectionNo() {
        long seq = inspectionRepository.count() + 1;
        return "QC" + Year.now().getValue() + "-" + String.format("%05d", seq);
    }

    private String generateCaNo() {
        long seq = correctiveActionRepository.count() + 1;
        return "CA" + Year.now().getValue() + "-" + String.format("%05d", seq);
    }
}
