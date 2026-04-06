package com.srm.notification.service;

import com.srm.approval.domain.ApprovalInstance;
import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.domain.ApprovalStep;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.repo.UserAccountRepository;
import com.srm.master.domain.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffNotificationService {

    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    /** 向当前审批层级、尚未处理的步骤所对应角色下的内部用户发站内信（去重用户）。 */
    public void notifyPendingApprovalApprovers(ApprovalInstance inst) {
        if (inst.getStatus() != ApprovalStatus.PENDING) {
            return;
        }
        int level = inst.getCurrentLevel();
        Set<String> rolesAtLevel = new HashSet<>();
        for (ApprovalStep s : inst.getSteps()) {
            if (s.getStepLevel() == level && s.getAction() == null) {
                rolesAtLevel.add(s.getApproverRole());
            }
        }
        if (rolesAtLevel.isEmpty()) {
            return;
        }
        String docLabel = approvalDocLabel(inst.getDocType());
        Set<Long> notified = new HashSet<>();
        for (String roleCode : rolesAtLevel) {
            for (UserAccount u : userAccountRepository.findInternalUsersByRoleCode(roleCode)) {
                if (!notified.add(u.getId())) {
                    continue;
                }
                try {
                    notificationService.send(
                            u.getId(),
                            null,
                            "待审批：" + docLabel,
                            docLabel + " " + inst.getDocNo() + " 待您审批，金额 " + inst.getTotalAmount() + "。",
                            "APPROVAL_PENDING",
                            "APPROVAL",
                            inst.getId());
                } catch (Exception e) {
                    log.warn("审批待办站内信失败 userId={}: {}", u.getId(), e.getMessage());
                }
            }
        }
    }

    public void notifyProcurementOrgStakeholders(Long orgId, String title, String content,
                                                 String category, String refType, Long refId) {
        if (orgId == null) {
            return;
        }
        for (UserAccount u : userAccountRepository.findInternalStakeholdersByOrg(orgId)) {
            try {
                notificationService.send(u.getId(), null, title, content, category, refType, refId);
            } catch (Exception e) {
                log.warn("组织干系人站内信失败 userId={}: {}", u.getId(), e.getMessage());
            }
        }
    }

    /** 向供应商已授权采购组织下的内部干系人广播（如绩效发布知会采购侧）。 */
    public void notifyInternalForSupplierScopedOrgs(Supplier supplier, String title, String content,
                                                    String category, String refType, Long refId) {
        if (supplier == null || supplier.getAuthorizedProcurementOrgs() == null) {
            return;
        }
        Set<Long> seenOrg = new HashSet<>();
        for (OrgUnit org : supplier.getAuthorizedProcurementOrgs()) {
            if (org == null || org.getId() == null || !seenOrg.add(org.getId())) {
                continue;
            }
            notifyProcurementOrgStakeholders(org.getId(), title, content, category, refType, refId);
        }
    }

    private static String approvalDocLabel(String docType) {
        if ("PR".equals(docType)) {
            return "请购单";
        }
        if ("PO".equals(docType)) {
            return "采购订单";
        }
        return docType != null ? docType : "单据";
    }
}
