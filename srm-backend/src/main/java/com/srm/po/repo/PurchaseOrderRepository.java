package com.srm.po.repo;

import com.srm.master.domain.Supplier;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findAllByIdIn(Collection<Long> ids);

    Optional<PurchaseOrder> findByPoNo(String poNo);

    @EntityGraph(attributePaths = {
            "lines", "lines.material", "lines.warehouse", "supplier", "procurementOrg", "ledger"
    })
    Optional<PurchaseOrder> findByProcurementOrg_IdAndPoNo(Long procurementOrgId, String poNo);

    @EntityGraph(attributePaths = {
            "lines", "lines.material", "lines.warehouse", "supplier", "procurementOrg", "ledger"
    })
    Optional<PurchaseOrder> findByProcurementOrg_IdAndU9DocNo(Long procurementOrgId, String u9DocNo);

    boolean existsByProcurementOrg_IdAndU9DocNo(Long procurementOrgId, String u9DocNo);

    @EntityGraph(attributePaths = {
            "lines", "lines.material", "lines.warehouse",
            "procurementOrg", "ledger", "supplier"
    })
    Optional<PurchaseOrder> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"supplier"})
    List<PurchaseOrder> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {"supplier"})
    Page<PurchaseOrder> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId, Pageable pageable);

    /**
     * 管理端列表：采购组织 + 可选模糊条件（均为空则等价于仅按组织分页）。
     */
    @EntityGraph(attributePaths = {"supplier"})
    @Query("""
            select p from PurchaseOrder p
            where p.procurementOrg.id = :oid
              and (:poNo is null or lower(p.poNo) like lower(concat('%', :poNo, '%')))
              and (:u9DocNo is null or (p.u9DocNo is not null
                  and lower(p.u9DocNo) like lower(concat('%', :u9DocNo, '%'))))
              and (:officialOrderNo is null or (p.u9OfficialOrderNo is not null
                  and lower(p.u9OfficialOrderNo) like lower(concat('%', :officialOrderNo, '%'))))
            order by p.id desc
            """)
    Page<PurchaseOrder> pageByProcurementOrgWithOptionalFilters(
            @Param("oid") Long procurementOrgId,
            @Param("poNo") String poNo,
            @Param("u9DocNo") String u9DocNo,
            @Param("officialOrderNo") String officialOrderNo,
            Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "procurementOrg"})
    List<PurchaseOrder> findBySupplierAndStatusOrderByIdDesc(Supplier supplier, PoStatus status);

    @EntityGraph(attributePaths = {"supplier", "procurementOrg"})
    Page<PurchaseOrder> findBySupplierAndStatusOrderByIdDesc(Supplier supplier, PoStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"lines", "lines.material", "supplier", "procurementOrg"})
    List<PurchaseOrder> findWithLinesBySupplierAndStatusOrderByIdDesc(Supplier supplier, PoStatus status);

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.warehouse", "ledger", "procurementOrg", "supplier"})
    @Query("select distinct p from PurchaseOrder p where p.procurementOrg.id = :oid and p.status in :statuses order by p.id desc")
    List<PurchaseOrder> findWithLinesForReport(@Param("oid") Long oid, @Param("statuses") Collection<PoStatus> statuses);

    @Query(value = "select coalesce(sum(pol.amount),0) from purchase_order_line pol " +
            "join purchase_order po on po.id = pol.purchase_order_id " +
            "where (:sid is null or po.supplier_id = :sid) and po.procurement_org_id = :oid " +
            "and DATE(po.created_at) >= :fromDate and DATE(po.created_at) <= :toDate " +
            "and po.status not in ('CANCELLED','DRAFT')", nativeQuery = true)
    BigDecimal sumAmountBySupplierAndOrgAndPeriod(@Param("sid") Long supplierId,
                                                   @Param("oid") Long orgId,
                                                   @Param("fromDate") LocalDate from,
                                                   @Param("toDate") LocalDate to);

    long countByProcurementOrgIdAndStatus(Long procurementOrgId, PoStatus status);

    /**
     * 已发布订单中，仍有可收货余额的订单行数（qty &gt; coalesce(receivedQty,0)）。
     */
    @Query("""
            select count(l) from PurchaseOrderLine l
            join l.purchaseOrder po
            where po.procurementOrg.id = :procurementOrgId
            and po.status = :released
            and l.qty > coalesce(l.receivedQty, 0)
            """)
    long countOpenReceiveLinesByProcurementOrg(@Param("procurementOrgId") Long procurementOrgId,
                                               @Param("released") PoStatus released);

    @Query("""
            select s.code, s.name, coalesce(sum(l.amount), 0)
            from PurchaseOrderLine l
            join l.purchaseOrder po
            join po.supplier s
            where po.procurementOrg.id = :oid
            and po.status not in :excluded
            and po.createdAt >= :from
            and po.createdAt < :toExclusive
            group by s.id, s.code, s.name
            order by coalesce(sum(l.amount), 0) desc
            """)
    List<Object[]> sumLineAmountGroupedBySupplier(@Param("oid") Long oid,
                                                  @Param("excluded") Collection<PoStatus> excluded,
                                                  @Param("from") Instant from,
                                                  @Param("toExclusive") Instant toExclusive);
}
