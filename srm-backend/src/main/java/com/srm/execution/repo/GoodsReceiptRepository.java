package com.srm.execution.repo;

import com.srm.execution.domain.GoodsReceipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query(value = "select coalesce(sum(grl.received_qty * pol.unit_price),0) " +
            "from goods_receipt_line grl " +
            "join goods_receipt gr on gr.id = grl.goods_receipt_id " +
            "join purchase_order_line pol on pol.id = grl.purchase_order_line_id " +
            "where gr.supplier_id = :sid and gr.procurement_org_id = :oid " +
            "and gr.receipt_date >= :fromDate and gr.receipt_date <= :toDate", nativeQuery = true)
    BigDecimal sumAmountBySupplierAndOrgAndPeriod(@Param("sid") Long supplierId,
                                                   @Param("oid") Long orgId,
                                                   @Param("fromDate") LocalDate from,
                                                   @Param("toDate") LocalDate to);

    long countByProcurementOrgId(Long procurementOrgId);
}
