package com.srm.execution.service;

import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public List<PurchaseExecutionRow> purchaseExecution(Long procurementOrgId) {
        List<PurchaseOrder> pos = purchaseOrderRepository.findWithLinesForReport(
                procurementOrgId, List.of(PoStatus.RELEASED, PoStatus.CLOSED));
        List<PurchaseExecutionRow> rows = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            for (PurchaseOrderLine l : po.getLines()) {
                BigDecimal open = l.getQty().subtract(l.getReceivedQty());
                if (open.compareTo(BigDecimal.ZERO) < 0) {
                    open = BigDecimal.ZERO;
                }
                rows.add(new PurchaseExecutionRow(
                        po.getPoNo(),
                        po.getStatus().name(),
                        l.getLineNo(),
                        l.getMaterial().getCode(),
                        l.getMaterial().getName(),
                        l.getQty(),
                        l.getReceivedQty(),
                        open
                ));
            }
        }
        return rows;
    }

    public record PurchaseExecutionRow(
            String poNo,
            String poStatus,
            int lineNo,
            String materialCode,
            String materialName,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal openQty
    ) {}
}
