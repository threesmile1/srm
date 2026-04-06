package com.srm.perf.repo;

import com.srm.perf.domain.PerfEvaluation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerfEvaluationRepository extends JpaRepository<PerfEvaluation, Long> {

    List<PerfEvaluation> findAllByOrderByIdDesc();

    List<PerfEvaluation> findBySupplierIdOrderByIdDesc(Long supplierId);

    @EntityGraph(attributePaths = {"scores", "scores.dimension", "supplier", "template", "template.dimensions"})
    Optional<PerfEvaluation> findWithDetailsById(Long id);
}
