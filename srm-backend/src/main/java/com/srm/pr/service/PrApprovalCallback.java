package com.srm.pr.service;

import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.service.ApprovalService;
import com.srm.pr.domain.PrStatus;
import com.srm.pr.domain.PurchaseRequisition;
import com.srm.pr.repo.PurchaseRequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrApprovalCallback implements ApprovalService.ApprovalCallback {

    private final PurchaseRequisitionRepository prRepository;

    @Override
    public boolean supports(String docType) {
        return "PR".equals(docType);
    }

    @Override
    @Transactional
    public void onApprovalComplete(String docType, Long docId, ApprovalStatus status) {
        prRepository.findById(docId).ifPresent(pr -> {
            if (status == ApprovalStatus.APPROVED) {
                pr.setStatus(PrStatus.APPROVED);
            } else if (status == ApprovalStatus.REJECTED) {
                pr.setStatus(PrStatus.REJECTED);
            }
            prRepository.save(pr);
            log.info("PR {} status updated to {} via approval callback", pr.getPrNo(), pr.getStatus());
        });
    }
}
