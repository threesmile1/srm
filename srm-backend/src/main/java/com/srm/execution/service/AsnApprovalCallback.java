package com.srm.execution.service;

import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.service.ApprovalService;
import com.srm.execution.domain.AsnNotice;
import com.srm.execution.repo.AsnNoticeRepository;
import com.srm.foundation.util.NingboProcurementOrg;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 宁波：ASN 客服确认记录回写（不阻断收货）。
 * <p>
 * 仅宁波采购组织的发货通知会发起审批（见 {@link AsnService#createFromSupplier}）；非宁波 ASN 不应存在审批实例。
 * 审批引擎会在 step 上记录审批人信息；此处将结果冗余写回 ASN，便于列表/详情展示。
 */
@Component
public class AsnApprovalCallback implements ApprovalService.ApprovalCallback {

    private final AsnNoticeRepository asnNoticeRepository;
    private final ApprovalService approvalService;

    public AsnApprovalCallback(AsnNoticeRepository asnNoticeRepository,
                               @Lazy ApprovalService approvalService) {
        this.asnNoticeRepository = asnNoticeRepository;
        this.approvalService = approvalService;
    }

    @Override
    public boolean supports(String docType) {
        return "ASN".equals(docType);
    }

    @Override
    @Transactional
    public void onApprovalComplete(String docType, Long docId, ApprovalStatus status) {
        AsnNotice n = asnNoticeRepository.findById(docId).orElse(null);
        if (n == null) {
            return;
        }
        if (!NingboProcurementOrg.isNingbo(n.getProcurementOrg())) {
            return;
        }
        // 从审批实例上取最后一步的审批人信息（若取不到则只落状态）
        var inst = approvalService.getInstanceByDoc("ASN", docId);
        Long approverId = null;
        String approverName = null;
        String comment = null;
        if (inst != null && inst.getSteps() != null) {
            for (var s : inst.getSteps()) {
                if (s.getAction() != null) {
                    approverId = s.getApproverId();
                    approverName = s.getApproverName();
                    comment = s.getComment();
                }
            }
        }

        n.setCsConfirmStatus(status == ApprovalStatus.APPROVED ? "CONFIRMED" : "REJECTED");
        n.setCsConfirmerId(approverId);
        n.setCsConfirmerName(approverName);
        n.setCsConfirmComment(comment);
        n.setCsConfirmedAt(Instant.now());
        asnNoticeRepository.save(n);
    }
}

