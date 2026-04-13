package com.srm.po.service;

import com.srm.approval.domain.ApprovalStatus;
import com.srm.approval.service.ApprovalService;
import com.srm.po.domain.PoStatus;
import com.srm.po.repo.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PoApprovalCallback implements ApprovalService.ApprovalCallback {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean supports(String docType) {
        return "PO".equals(docType);
    }

    @Override
    @Transactional
    public void onApprovalComplete(String docType, Long docId, ApprovalStatus status) {
        purchaseOrderRepository.findById(docId).ifPresent(po -> {
            if (status == ApprovalStatus.APPROVED) {
                po.setStatus(PoStatus.APPROVED);
            } else if (status == ApprovalStatus.REJECTED) {
                po.setStatus(PoStatus.DRAFT);
            }
            purchaseOrderRepository.save(po);
            log.info("PO {} status updated to {} via approval callback", po.getPoNo(), po.getStatus());

            if (status == ApprovalStatus.APPROVED) {
                eventPublisher.publishEvent(new PoApprovedEvent(po.getId(), po.getPoNo()));
            }
        });
    }
}
