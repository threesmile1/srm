package com.srm.execution.repo;

import com.srm.execution.domain.AsnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface AsnLineRepository extends JpaRepository<AsnLine, Long> {

    @Query("""
            select coalesce(sum(l.shipQty), 0) from AsnLine l
            join l.asnNotice a
            where l.purchaseOrderLine.id = :polId and a.status = 'SUBMITTED'
            """)
    BigDecimal sumShipQtySubmittedForPolLine(@Param("polId") Long polId);
}
