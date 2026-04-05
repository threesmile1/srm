package com.srm.po.repo;

import com.srm.po.domain.PoNumberSeq;
import com.srm.po.domain.PoNumberSeqId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PoNumberSeqRepository extends JpaRepository<PoNumberSeq, PoNumberSeqId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PoNumberSeq s where s.id.procurementOrgId = :oid and s.id.yearVal = :year")
    Optional<PoNumberSeq> findForUpdate(@Param("oid") Long oid, @Param("year") int year);
}
