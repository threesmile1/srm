package com.srm.invoice.repo;

import com.srm.invoice.domain.ReconStatus;
import com.srm.invoice.domain.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {

    @Query("""
            select distinct r from Reconciliation r
            join fetch r.supplier join fetch r.procurementOrg
            where r.procurementOrg.id = :orgId
            and r.status <> :cancelled
            order by r.id desc""")
    List<Reconciliation> findWithDetailsByProcurementOrgIdOrderByIdDesc(
            @Param("orgId") Long orgId, @Param("cancelled") ReconStatus cancelled);

    @Query("""
            select distinct r from Reconciliation r
            join fetch r.supplier join fetch r.procurementOrg
            where r.supplier.id = :supplierId
            and r.status <> :cancelled
            order by r.id desc""")
    List<Reconciliation> findWithDetailsBySupplierIdOrderByIdDesc(
            @Param("supplierId") Long supplierId, @Param("cancelled") ReconStatus cancelled);
}
