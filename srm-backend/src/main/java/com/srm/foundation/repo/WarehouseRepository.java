package com.srm.foundation.repo;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByProcurementOrgOrderByCodeAsc(OrgUnit procurementOrg);

    boolean existsByProcurementOrgAndCode(OrgUnit procurementOrg, String code);

    Optional<Warehouse> findByCode(String code);

    Optional<Warehouse> findByProcurementOrgAndCode(OrgUnit procurementOrg, String code);
}
