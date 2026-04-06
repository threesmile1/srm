package com.srm.master.service;

import com.srm.master.domain.Supplier;
import com.srm.master.domain.SupplierAudit;
import com.srm.master.domain.SupplierLifecycleStatus;
import com.srm.master.repo.SupplierAuditRepository;
import com.srm.master.repo.SupplierRepository;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierLifecycleService {

    private final SupplierRepository supplierRepository;
    private final SupplierAuditRepository supplierAuditRepository;

    @Transactional
    public Supplier updateLifecycleStatus(Long supplierId, SupplierLifecycleStatus newStatus) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("供应商不存在: " + supplierId));
        supplier.setLifecycleStatus(newStatus);
        return supplierRepository.save(supplier);
    }

    @Transactional
    public SupplierAudit addAudit(Long supplierId, String auditType, LocalDate auditDate,
                                  String result, Integer score, String auditorName, String remark) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("供应商不存在: " + supplierId));

        SupplierAudit audit = new SupplierAudit();
        audit.setSupplier(supplier);
        audit.setAuditType(auditType);
        audit.setAuditDate(auditDate);
        audit.setResult(result);
        audit.setScore(score);
        audit.setAuditorName(auditorName);
        audit.setRemark(remark);

        if ("ADMISSION".equalsIgnoreCase(auditType)) {
            if ("PASS".equalsIgnoreCase(result)) {
                supplier.setLifecycleStatus(SupplierLifecycleStatus.QUALIFIED);
            } else if ("FAIL".equalsIgnoreCase(result)) {
                supplier.setLifecycleStatus(SupplierLifecycleStatus.RECTIFICATION);
            }
            supplierRepository.save(supplier);
        }

        return supplierAuditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<SupplierAudit> listAudits(Long supplierId) {
        return supplierAuditRepository.findBySupplierIdOrderByIdDesc(supplierId);
    }
}
