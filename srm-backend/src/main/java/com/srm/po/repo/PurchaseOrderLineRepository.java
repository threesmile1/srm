package com.srm.po.repo;

import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrderLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.supplier", "material", "warehouse"})
    @Query("select l from PurchaseOrderLine l where l.id = :id")
    Optional<PurchaseOrderLine> findWithPoById(@Param("id") Long id);

    /** 已发布订单中供应商尚未确认交期/数量的行数 */
    @Query("""
            select count(l) from PurchaseOrderLine l
            join l.purchaseOrder po
            where po.supplier.id = :supplierId
            and po.status = :released
            and l.confirmedAt is null
            """)
    long countUnconfirmedLinesForSupplierReleasedOrders(@Param("supplierId") Long supplierId,
                                                      @Param("released") PoStatus released);

    /**
     * 各采购订单尚未收清数量：按行 max(0, 订购数量 - 累计实收)。
     */
    @Query("""
            select l.purchaseOrder.id,
                   coalesce(sum(case when l.receivedQty < l.qty then l.qty - l.receivedQty else 0 end), 0)
            from PurchaseOrderLine l
            where l.purchaseOrder.id in :poIds
            group by l.purchaseOrder.id
            """)
    List<Object[]> sumPendingReceiptQtyByPurchaseOrderIds(@Param("poIds") Set<Long> poIds);

    /**
     * 供应商开票选行：已发布或已关闭订单、本组织、已有实收数量的订单行。
     * 说明：收货后订单常被采购关闭（CLOSED），若仅筛 RELEASED 会导致「有收货但选行无数据」。
     */
    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.procurementOrg", "purchaseOrder.supplier", "material"})
    @Query("""
            select l from PurchaseOrderLine l
            join l.purchaseOrder po
            where po.supplier.id = :sid and po.procurementOrg.id = :oid
            and po.status in (com.srm.po.domain.PoStatus.RELEASED, com.srm.po.domain.PoStatus.CLOSED)
            and coalesce(l.receivedQty, 0) > 0
            order by po.id desc, l.lineNo asc
            """)
    List<PurchaseOrderLine> findReleasedWithReceiptBySupplierAndOrg(@Param("sid") Long supplierId,
                                                                   @Param("oid") Long procurementOrgId);
}
