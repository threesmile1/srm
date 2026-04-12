package com.srm.invoice.repo;

import com.srm.invoice.domain.Invoice;
import com.srm.invoice.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** 列表 DTO 需 supplier 编码/名称；避免事务外访问懒加载导致 LazyInitializationException → 500 */
    @EntityGraph(attributePaths = "supplier")
    List<Invoice> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = "supplier")
    List<Invoice> findBySupplierIdOrderByIdDesc(Long supplierId);

    List<Invoice> findBySupplierIdAndStatusOrderByIdDesc(Long supplierId, InvoiceStatus status);

    /**
     * 不可与 {@code attachments} 同图一并 fetch：Hibernate 禁止同一查询同时 join 两个 List（Bag），会报 MultipleBagFetchException。
     * 附件在事务内对 {@code Invoice.attachments} 懒加载即可。
     */
    @EntityGraph(attributePaths = {"lines", "lines.purchaseOrder", "lines.purchaseOrderLine",
            "lines.goodsReceipt", "supplier", "procurementOrg"})
    Optional<Invoice> findWithDetailsById(Long id);

    @Query("select coalesce(sum(i.totalAmount),0) from Invoice i " +
            "where i.supplier.id = :sid and i.procurementOrg.id = :oid " +
            "and i.invoiceDate >= :from and i.invoiceDate <= :to " +
            "and i.status in (com.srm.invoice.domain.InvoiceStatus.SUBMITTED, com.srm.invoice.domain.InvoiceStatus.CONFIRMED)")
    BigDecimal sumAmountBySupplierAndOrgAndPeriod(@Param("sid") Long supplierId,
                                                   @Param("oid") Long orgId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    /** 对账汇总：仅统计采购已确认发票（甄云类 SRM 常以「确认后」金额进入对账/结算口径） */
    @Query("select coalesce(sum(i.totalAmount),0) from Invoice i " +
            "where i.supplier.id = :sid and i.procurementOrg.id = :oid " +
            "and i.invoiceDate >= :from and i.invoiceDate <= :to " +
            "and i.status = com.srm.invoice.domain.InvoiceStatus.CONFIRMED")
    BigDecimal sumConfirmedAmountBySupplierAndOrgAndPeriod(@Param("sid") Long supplierId,
                                                            @Param("oid") Long orgId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

    /**
     * 对账按<strong>收货月</strong>：仅统计已确认发票中、关联 {@link com.srm.execution.domain.GoodsReceipt}
     * 且收货日期落在区间内的<strong>发票行金额</strong>（无收货关联的行不计入）。
     */
    @Query("""
            select coalesce(sum(il.amount), 0) from InvoiceLine il
            join il.invoice i
            join il.goodsReceipt gr
            where i.supplier.id = :sid and i.procurementOrg.id = :oid
            and i.status = com.srm.invoice.domain.InvoiceStatus.CONFIRMED
            and gr.receiptDate >= :from and gr.receiptDate <= :to""")
    BigDecimal sumConfirmedLineAmountByGrReceiptDateInPeriod(@Param("sid") Long supplierId,
                                                             @Param("oid") Long orgId,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    long countByProcurementOrgIdAndStatus(Long procurementOrgId, InvoiceStatus status);

    /** 已提交/已确认发票中，关联同一订单行的累计开票数量（三单匹配用） */
    @Query("""
            select coalesce(sum(il.qty), 0) from InvoiceLine il
            join il.invoice i
            where il.purchaseOrderLine.id = :polId
            and i.status in (com.srm.invoice.domain.InvoiceStatus.SUBMITTED, com.srm.invoice.domain.InvoiceStatus.CONFIRMED)
            """)
    BigDecimal sumInvoicedQtyByPurchaseOrderLineId(@Param("polId") Long polId);
}
