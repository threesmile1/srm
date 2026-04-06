package com.srm.master.repo;

import com.srm.master.domain.SupplierAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierAuditRepository extends JpaRepository<SupplierAudit, Long> {

    List<SupplierAudit> findBySupplierIdOrderByIdDesc(Long supplierId);
}
