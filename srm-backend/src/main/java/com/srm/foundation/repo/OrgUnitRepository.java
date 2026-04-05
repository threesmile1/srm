package com.srm.foundation.repo;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    List<OrgUnit> findByLedgerOrderByCodeAsc(Ledger ledger);

    List<OrgUnit> findByLedgerAndOrgTypeOrderByCodeAsc(Ledger ledger, OrgUnitType orgType);

    boolean existsByLedgerAndCode(Ledger ledger, String code);
}
