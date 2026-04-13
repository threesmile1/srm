package com.srm.po.service;

import com.srm.po.domain.PoStatus;
import com.srm.po.repo.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PO 审批通过后自动发布到供应商。
 * <p>
 * 监听事务提交后的事件，避免审批回调内直接调用发布导致循环依赖或事务副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoAutoReleaseListener {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderService purchaseOrderService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPoApproved(PoApprovedEvent evt) {
        if (evt == null || evt.poId() == null) {
            return;
        }
        try {
            var po = purchaseOrderRepository.findById(evt.poId()).orElse(null);
            if (po == null) {
                return;
            }
            if (po.getStatus() != PoStatus.APPROVED) {
                return;
            }
            purchaseOrderService.release(po.getId());
            log.info("PO {} auto released after approval", po.getPoNo());
        } catch (Exception e) {
            log.warn("PO {} auto release failed after approval: {}", evt.poNo(), e.getMessage());
        }
    }
}

