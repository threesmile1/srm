package com.srm.pr.repo;

import com.srm.pr.domain.PurchaseRequisition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    /**
     * 显式按采购组织主键过滤；避免派生方法名与仅存在 {@code procurementOrg} 关联时的歧义。
     * join fetch 便于列表汇总时读取组织编码（LAZY）。
     */
    @Query(
            "select p from PurchaseRequisition p join fetch p.procurementOrg o where o.id = :procurementOrgId order by p.id desc")
    List<PurchaseRequisition> findByProcurementOrgIdOrderByIdDesc(
            @Param("procurementOrgId") Long procurementOrgId);

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.warehouse", "lines.supplier",
            "lines.convertedPo", "procurementOrg", "ledger"})
    Optional<PurchaseRequisition> findWithDetailsById(Long id);
}
