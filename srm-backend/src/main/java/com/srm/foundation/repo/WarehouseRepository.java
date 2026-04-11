package com.srm.foundation.repo;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByProcurementOrgOrderByCodeAsc(OrgUnit procurementOrg);

    boolean existsByProcurementOrgAndCode(OrgUnit procurementOrg, String code);

    Optional<Warehouse> findByCode(String code);

    Optional<Warehouse> findByProcurementOrgAndCode(OrgUnit procurementOrg, String code);

    @EntityGraph(attributePaths = {"procurementOrg", "procurementOrg.ledger"})
    @Query("select w from Warehouse w join w.procurementOrg o order by o.code asc, w.code asc")
    Page<Warehouse> findAllPaged(Pageable pageable);
}
