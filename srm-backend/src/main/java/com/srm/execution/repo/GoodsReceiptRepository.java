package com.srm.execution.repo;

import com.srm.execution.domain.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "warehouse"})
    List<GoodsReceipt> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {
            "lines", "lines.purchaseOrderLine", "lines.purchaseOrderLine.material",
            "lines.asnLine", "lines.asnLine.asnNotice",
            "warehouse", "supplier", "purchaseOrder", "ledger", "procurementOrg"
    })
    @Query("select g from GoodsReceipt g where g.id = :id")
    Optional<GoodsReceipt> findWithDetailsById(@Param("id") Long id);

    List<GoodsReceipt> findAllByIdIn(Collection<Long> ids);
}
