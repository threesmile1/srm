package com.srm.quality.repo;

import com.srm.quality.domain.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {

    List<CorrectiveAction> findByProcurementOrgIdOrderByIdDesc(Long orgId);

    List<CorrectiveAction> findBySupplierIdOrderByIdDesc(Long supplierId);
}
