package com.srm.invoice.repo;

import com.srm.invoice.domain.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {

    List<Reconciliation> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    List<Reconciliation> findBySupplierIdOrderByIdDesc(Long supplierId);
}
