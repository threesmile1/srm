package com.srm.quality.repo;

import com.srm.quality.domain.QualityInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualityInspectionRepository extends JpaRepository<QualityInspection, Long> {

    List<QualityInspection> findByProcurementOrgIdOrderByIdDesc(Long orgId);

    List<QualityInspection> findBySupplierIdOrderByIdDesc(Long supplierId);
}
