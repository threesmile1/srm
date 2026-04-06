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

    List<Invoice> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    List<Invoice> findBySupplierIdOrderByIdDesc(Long supplierId);

    List<Invoice> findBySupplierIdAndStatusOrderByIdDesc(Long supplierId, InvoiceStatus status);

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
