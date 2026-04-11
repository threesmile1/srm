package com.srm.foundation.repo;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    List<OrgUnit> findByLedgerOrderByCodeAsc(Ledger ledger);

    List<OrgUnit> findByLedgerAndOrgTypeOrderByCodeAsc(Ledger ledger, OrgUnitType orgType);

    List<OrgUnit> findByOrgType(OrgUnitType orgType);

    List<OrgUnit> findByOrgTypeOrderByCodeAsc(OrgUnitType orgType);

    boolean existsByLedgerAndCode(Ledger ledger, String code);

    Optional<OrgUnit> findByCode(String code);

    Optional<OrgUnit> findFirstByOrgTypeAndU9OrgCode(OrgUnitType orgType, String u9OrgCode);

    Optional<OrgUnit> findFirstByOrgTypeAndCode(OrgUnitType orgType, String code);

    Optional<OrgUnit> findFirstByOrgTypeAndName(OrgUnitType orgType, String name);
}
