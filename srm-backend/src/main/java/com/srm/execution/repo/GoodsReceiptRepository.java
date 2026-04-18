package com.srm.execution.repo;

import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.AsnStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "warehouse"})
    List<GoodsReceipt> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "warehouse"})
    Page<GoodsReceipt> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId, Pageable pageable);

    /**
     * 「待收货的发货通知」页签：本组织下，关联订单仍有未收清数量，且（本单至少一行关联 ASN 或 订单存在已提交 ASN）。
     */
    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "warehouse"})
    @Query("""
            select gr from GoodsReceipt gr
            where gr.procurementOrg.id = :oid
              and exists (
                select 1 from PurchaseOrderLine pol
                where pol.purchaseOrder = gr.purchaseOrder
                  and pol.qty > coalesce(pol.receivedQty, 0)
              )
              and (
                exists (
                  select 1 from GoodsReceiptLine gl
                  where gl.goodsReceipt = gr and gl.asnLine is not null
                )
                or exists (
                  select 1 from AsnNotice a
                  where a.purchaseOrder = gr.purchaseOrder and a.status = :submitted
                )
              )
            order by gr.id desc
            """)
    Page<GoodsReceipt> pageWaitReceiveByOrg(@Param("oid") Long procurementOrgId,
                                           @Param("submitted") AsnStatus submitted,
                                           Pageable pageable);

    /**
     * 宁波「待收货的发货通知」：关联订单仍有未收清数量，且存在已提交、尚未客服确认（非 CONFIRMED）的发货通知。
     */
    @EntityGraph(attributePaths = {"purchaseOrder", "supplier", "warehouse"})
    @Query("""
            select gr from GoodsReceipt gr
            where gr.procurementOrg.id = :oid
              and exists (
                select 1 from PurchaseOrderLine pol
                where pol.purchaseOrder = gr.purchaseOrder
                  and pol.qty > coalesce(pol.receivedQty, 0)
              )
              and exists (
                select 1 from AsnNotice a
                where a.purchaseOrder = gr.purchaseOrder
                  and a.status = :submitted
                  and (a.csConfirmStatus is null or a.csConfirmStatus <> 'CONFIRMED')
              )
            order by gr.id desc
            """)
    Page<GoodsReceipt> pageWaitReceiveNingboPendingCsConfirm(@Param("oid") Long procurementOrgId,
                                                             @Param("submitted") AsnStatus submitted,
                                                             Pageable pageable);

    @Query("select distinct gr.purchaseOrder.id from GoodsReceipt gr where gr.procurementOrg.id = :oid")
    Set<Long> findDistinctPurchaseOrderIdsByProcurementOrgId(@Param("oid") Long procurementOrgId);

    @EntityGraph(attributePaths = {
            "lines", "lines.purchaseOrderLine", "lines.purchaseOrderLine.material",
            "lines.asnLine", "lines.asnLine.asnNotice",
            "warehouse", "supplier", "purchaseOrder", "ledger", "procurementOrg"
    })
    @Query("select g from GoodsReceipt g where g.id = :id")
    Optional<GoodsReceipt> findWithDetailsById(@Param("id") Long id);

    List<GoodsReceipt> findAllByIdIn(Collection<Long> ids);

    Optional<GoodsReceipt> findByProcurementOrg_IdAndU9DocNo(Long procurementOrgId, String u9DocNo);

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

    /** 宁波：对账/开票门禁 - 查询期间内首个 U9 收货单非“业务关闭”的记录（用于报错提示）。 */
    @Query(value = "select gr.gr_no, gr.u9_status " +
            "from goods_receipt gr " +
            "where gr.supplier_id = :sid and gr.procurement_org_id = :oid " +
            "and gr.source_system = 'U9' " +
            "and gr.receipt_date >= :fromDate and gr.receipt_date <= :toDate " +
            "and (gr.u9_status is null or gr.u9_status <> '业务关闭') " +
            "order by gr.id desc " +
            "limit 1", nativeQuery = true)
    Object[] findFirstU9NotBusinessClosedInPeriod(@Param("sid") Long supplierId,
                                                  @Param("oid") Long orgId,
                                                  @Param("fromDate") LocalDate from,
                                                  @Param("toDate") LocalDate to);

    long countByProcurementOrgId(Long procurementOrgId);
}
