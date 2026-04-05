package com.srm.po.repo;

import com.srm.po.domain.PurchaseOrderLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier"})
    @Query("select l from PurchaseOrderLine l where l.id = :id")
    Optional<PurchaseOrderLine> findWithPoById(@Param("id") Long id);
}
