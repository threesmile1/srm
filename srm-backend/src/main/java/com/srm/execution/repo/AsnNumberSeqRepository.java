package com.srm.execution.repo;

import com.srm.execution.domain.AsnNumberSeq;
import com.srm.execution.domain.AsnNumberSeqId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AsnNumberSeqRepository extends JpaRepository<AsnNumberSeq, AsnNumberSeqId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AsnNumberSeq s where s.id.procurementOrgId = :oid and s.id.yearVal = :year")
    Optional<AsnNumberSeq> findForUpdate(@Param("oid") Long oid, @Param("year") int year);
}
