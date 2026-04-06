package com.srm.pr.repo;

import com.srm.pr.domain.PurchaseRequisition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    List<PurchaseRequisition> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.warehouse", "lines.supplier",
            "lines.convertedPo", "procurementOrg", "ledger"})
    Optional<PurchaseRequisition> findWithDetailsById(Long id);
}
