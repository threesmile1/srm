package com.srm.po.repo;

import com.srm.master.domain.Supplier;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findAllByIdIn(Collection<Long> ids);

    Optional<PurchaseOrder> findByPoNo(String poNo);

    @EntityGraph(attributePaths = {
            "lines", "lines.material", "lines.warehouse",
            "procurementOrg", "ledger", "supplier", "supplier.authorizedProcurementOrgs"
    })
    Optional<PurchaseOrder> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"supplier"})
    List<PurchaseOrder> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {"supplier", "procurementOrg"})
    List<PurchaseOrder> findBySupplierAndStatusOrderByIdDesc(Supplier supplier, PoStatus status);

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.warehouse", "ledger", "procurementOrg", "supplier"})
    @Query("select distinct p from PurchaseOrder p where p.procurementOrg.id = :oid and p.status in :statuses order by p.id desc")
    List<PurchaseOrder> findWithLinesForReport(@Param("oid") Long oid, @Param("statuses") Collection<PoStatus> statuses);
}
