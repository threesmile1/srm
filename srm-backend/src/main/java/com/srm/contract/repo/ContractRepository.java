package com.srm.contract.repo;

import com.srm.contract.domain.Contract;
import com.srm.contract.domain.ContractStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByProcurementOrgIdOrderByIdDesc(Long orgId);

    List<Contract> findBySupplierIdOrderByIdDesc(Long supplierId);

    @EntityGraph(attributePaths = {"lines", "lines.material", "supplier", "procurementOrg"})
    Optional<Contract> findWithDetailsById(Long id);

    List<Contract> findByStatusAndEndDateBefore(ContractStatus status, LocalDate date);

    @EntityGraph(attributePaths = {"supplier", "procurementOrg"})
    List<Contract> findByStatusAndEndDate(ContractStatus status, LocalDate endDate);
}
