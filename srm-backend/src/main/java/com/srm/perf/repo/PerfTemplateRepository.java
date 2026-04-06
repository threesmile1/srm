package com.srm.perf.repo;

import com.srm.perf.domain.PerfTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerfTemplateRepository extends JpaRepository<PerfTemplate, Long> {

    @EntityGraph(attributePaths = {"dimensions"})
    Optional<PerfTemplate> findWithDimensionsById(Long id);

    List<PerfTemplate> findByEnabledTrueOrderByIdAsc();
}
