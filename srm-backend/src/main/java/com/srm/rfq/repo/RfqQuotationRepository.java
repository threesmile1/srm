package com.srm.rfq.repo;

import com.srm.rfq.domain.RfqQuotation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RfqQuotationRepository extends JpaRepository<RfqQuotation, Long> {

    @EntityGraph(attributePaths = {"supplier"})
    List<RfqQuotation> findByRfqIdOrderByIdDesc(Long rfqId);

    Optional<RfqQuotation> findByRfqIdAndSupplierId(Long rfqId, Long supplierId);

    @EntityGraph(attributePaths = {
            "quotationLines", "quotationLines.rfqLine",
            "supplier"
    })
    Optional<RfqQuotation> findWithDetailsById(Long id);
}
