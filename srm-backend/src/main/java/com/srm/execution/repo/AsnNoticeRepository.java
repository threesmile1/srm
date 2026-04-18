package com.srm.execution.repo;

import com.srm.execution.domain.AsnNotice;
import com.srm.execution.domain.AsnStatus;
import com.srm.master.domain.Supplier;
import com.srm.po.domain.PurchaseOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsnNoticeRepository extends JpaRepository<AsnNotice, Long> {

    @EntityGraph(attributePaths = {
            "lines", "lines.purchaseOrderLine", "lines.purchaseOrderLine.material", "purchaseOrder"
    })
    List<AsnNotice> findByPurchaseOrderOrderByIdDesc(PurchaseOrder purchaseOrder);

    @EntityGraph(attributePaths = {
            "lines", "lines.purchaseOrderLine", "lines.purchaseOrderLine.material", "purchaseOrder"
    })
    @Query("select a from AsnNotice a where a.id = :id")
    Optional<AsnNotice> findWithLinesById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "purchaseOrder", "lines", "lines.purchaseOrderLine", "lines.purchaseOrderLine.material"
    })
    List<AsnNotice> findBySupplierOrderByIdDesc(Supplier supplier);

    long countBySupplier_Id(Long supplierId);

    long countBySupplier_IdAndStatus(Long supplierId, AsnStatus status);

    @Query("select distinct a.purchaseOrder.id from AsnNotice a where a.purchaseOrder.id in :poIds and a.status = 'SUBMITTED'")
    List<Long> findPurchaseOrderIdsHavingSubmittedAsn(@Param("poIds") Collection<Long> poIds);

    /** 宁波：已提交且尚未客服确认通过（含未回写、已驳回等非 CONFIRMED）的发货通知 */
    @Query("""
            select distinct a.purchaseOrder.id from AsnNotice a
            where a.purchaseOrder.id in :poIds and a.status = 'SUBMITTED'
              and (a.csConfirmStatus is null or a.csConfirmStatus <> 'CONFIRMED')
            """)
    List<Long> findPurchaseOrderIdsHavingSubmittedAsnPendingCsConfirm(@Param("poIds") Collection<Long> poIds);

    Optional<AsnNotice> findFirstByPurchaseOrder_IdAndStatusOrderByIdDesc(Long purchaseOrderId, AsnStatus status);

    @Query("""
            select a from AsnNotice a
            where a.purchaseOrder.id = :poId and a.status = :st
              and (a.csConfirmStatus is null or a.csConfirmStatus <> 'CONFIRMED')
            order by a.id desc
            """)
    List<AsnNotice> findSubmittedAsnPendingCsByPurchaseOrder(@Param("poId") Long purchaseOrderId,
                                                           @Param("st") AsnStatus status,
                                                           Pageable pageable);
}
