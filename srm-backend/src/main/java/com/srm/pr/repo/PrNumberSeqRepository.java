package com.srm.pr.repo;

import com.srm.pr.domain.PrNumberSeq;
import com.srm.pr.domain.PrNumberSeqId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrNumberSeqRepository extends JpaRepository<PrNumberSeq, PrNumberSeqId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PrNumberSeq s where s.procurementOrgId = :oid and s.yearVal = :year")
    Optional<PrNumberSeq> findForUpdate(@Param("oid") Long oid, @Param("year") int year);
}
