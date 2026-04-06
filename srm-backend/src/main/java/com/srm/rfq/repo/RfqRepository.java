package com.srm.rfq.repo;

import com.srm.rfq.domain.Rfq;
import com.srm.rfq.domain.RfqStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RfqRepository extends JpaRepository<Rfq, Long> {

    @EntityGraph(attributePaths = {"procurementOrg"})
    List<Rfq> findByProcurementOrgIdOrderByIdDesc(Long procurementOrgId);

    @EntityGraph(attributePaths = {
            "lines", "lines.material",
            "invitations", "invitations.supplier",
            "procurementOrg"
    })
    Optional<Rfq> findWithDetailsById(Long id);

    /** 已发布、未报价、未过截止日的受邀询价单数量（门户待办） */
    @Query("""
            select count(distinct r.id) from Rfq r
            join r.invitations inv
            where inv.supplier.id = :supplierId
            and r.status = :published
            and inv.responded = false
            and (r.deadline is null or r.deadline >= :today)
            """)
    long countAwaitingSupplierQuotation(
            @Param("supplierId") long supplierId,
            @Param("published") RfqStatus published,
            @Param("today") LocalDate today);

    /** 门户：受邀且状态在集合内的询价（避免 findAll 全表扫描） */
    @EntityGraph(attributePaths = {"procurementOrg"})
    @Query("""
            select distinct r from Rfq r
            join r.invitations inv
            where inv.supplier.id = :supplierId
            and r.status in :statuses
            order by r.id desc
            """)
    List<Rfq> findInvitedForSupplierWithStatuses(
            @Param("supplierId") long supplierId,
            @Param("statuses") List<RfqStatus> statuses);
}
