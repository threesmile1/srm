package com.srm.approval.service;

import com.srm.approval.domain.*;
import com.srm.approval.repo.ApprovalInstanceRepository;
import com.srm.approval.repo.ApprovalRuleRepository;
import com.srm.foundation.service.AuditService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRuleRepository ruleRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final AuditService auditService;
    private final StaffNotificationService staffNotificationService;

    public List<ApprovalRule> listRules() {
        return ruleRepository.findAllByOrderByDocTypeAscMinAmountAscApprovalLevelAsc();
    }

    @Transactional
    public ApprovalRule saveRule(ApprovalRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    /**
     * Start an approval workflow for a document.
     * Finds matching rules by docType + amount range, creates steps for each level.
     */
    @Transactional
    public ApprovalInstance startApproval(String docType, Long docId, String docNo, BigDecimal totalAmount) {
        if (instanceRepository.findByDocTypeAndDocId(docType, docId).isPresent()) {
            throw new BadRequestException("该单据已存在审批流程");
        }

        List<ApprovalRule> rules = ruleRepository.findByDocTypeAndEnabledTrueOrderByMinAmountAscApprovalLevelAsc(docType);
        List<ApprovalRule> matched = rules.stream()
                .filter(r -> {
                    boolean aboveMin = totalAmount.compareTo(r.getMinAmount()) >= 0;
                    boolean belowMax = r.getMaxAmount() == null || totalAmount.compareTo(r.getMaxAmount()) < 0;
                    return aboveMin && belowMax;
                })
                .sorted(Comparator.comparingInt(ApprovalRule::getApprovalLevel))
                .toList();

        if (matched.isEmpty()) {
            throw new BadRequestException("未找到匹配的审批规则 docType=" + docType + " amount=" + totalAmount);
        }

        ApprovalInstance inst = new ApprovalInstance();
        inst.setDocType(docType);
        inst.setDocId(docId);
        inst.setDocNo(docNo);
        inst.setTotalAmount(totalAmount);
        inst.setStatus(ApprovalStatus.PENDING);
        inst.setCurrentLevel(1);

        for (ApprovalRule r : matched) {
            ApprovalStep step = new ApprovalStep();
            step.setInstance(inst);
            step.setStepLevel(r.getApprovalLevel());
            step.setApproverRole(r.getApproverRole());
            inst.getSteps().add(step);
        }

        ApprovalInstance saved = instanceRepository.save(inst);
        auditService.log(null, null, "START_APPROVAL", docType, docId,
                "docNo=" + docNo + " levels=" + matched.stream().mapToInt(ApprovalRule::getApprovalLevel).max().orElse(0), null);
        staffNotificationService.notifyPendingApprovalApprovers(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public ApprovalInstance getInstance(Long instanceId) {
        return instanceRepository.findWithStepsById(instanceId)
                .orElseThrow(() -> new NotFoundException("审批流程不存在: " + instanceId));
    }

    @Transactional(readOnly = true)
    public ApprovalInstance getInstanceByDoc(String docType, Long docId) {
        return instanceRepository.findByDocTypeAndDocId(docType, docId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstance> listPending() {
        return instanceRepository.findByStatusOrderByIdDesc(ApprovalStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ApprovalInstance> listAll() {
        return instanceRepository.findAllByOrderByIdDesc();
    }

    /**
     * Process an approval action (approve or reject) on the current level step.
     */
    @Transactional
    public ApprovalInstance processAction(Long instanceId, StepAction action, Long approverId,
                                           String approverName, String comment) {
        ApprovalInstance inst = getInstance(instanceId);
        if (inst.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException("审批流程已结束，当前状态: " + inst.getStatus());
        }

        ApprovalStep currentStep = inst.getSteps().stream()
                .filter(s -> s.getStepLevel() == inst.getCurrentLevel() && s.getAction() == null)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("当前级别无待审批步骤"));

        currentStep.setAction(action);
        currentStep.setApproverId(approverId);
        currentStep.setApproverName(approverName);
        currentStep.setComment(comment);
        currentStep.setActedAt(Instant.now());

        if (action == StepAction.REJECTED) {
            inst.setStatus(ApprovalStatus.REJECTED);
        } else {
            int maxLevel = inst.getSteps().stream()
                    .mapToInt(ApprovalStep::getStepLevel).max().orElse(1);
            if (inst.getCurrentLevel() >= maxLevel) {
                inst.setStatus(ApprovalStatus.APPROVED);
            } else {
                inst.setCurrentLevel(inst.getCurrentLevel() + 1);
            }
        }

        auditService.log(approverId, approverName, "APPROVAL_" + action,
                inst.getDocType(), inst.getDocId(),
                "docNo=" + inst.getDocNo() + " level=" + currentStep.getStepLevel(), null);

        ApprovalInstance saved = instanceRepository.save(inst);

        if (saved.getStatus() == ApprovalStatus.PENDING) {
            staffNotificationService.notifyPendingApprovalApprovers(saved);
        }

        if (saved.getStatus() == ApprovalStatus.APPROVED || saved.getStatus() == ApprovalStatus.REJECTED) {
            for (ApprovalCallback cb : callbacks) {
                if (cb.supports(saved.getDocType())) {
                    cb.onApprovalComplete(saved.getDocType(), saved.getDocId(), saved.getStatus());
                }
            }
        }

        return saved;
    }

    public interface ApprovalCallback {
        boolean supports(String docType);
        void onApprovalComplete(String docType, Long docId, ApprovalStatus status);
    }

    private final List<ApprovalCallback> callbacks;
}
