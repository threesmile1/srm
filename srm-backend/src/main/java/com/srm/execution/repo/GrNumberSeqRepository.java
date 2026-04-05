package com.srm.execution.repo;

import com.srm.execution.domain.GrNumberSeq;
import com.srm.execution.domain.GrNumberSeqId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GrNumberSeqRepository extends JpaRepository<GrNumberSeq, GrNumberSeqId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GrNumberSeq s where s.id.procurementOrgId = :oid and s.id.yearVal = :year")
    Optional<GrNumberSeq> findForUpdate(@Param("oid") Long oid, @Param("year") int year);
}
