package com.srm.foundation.repo;

import com.srm.foundation.domain.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    Optional<Ledger> findByCode(String code);

    boolean existsByCode(String code);
}
