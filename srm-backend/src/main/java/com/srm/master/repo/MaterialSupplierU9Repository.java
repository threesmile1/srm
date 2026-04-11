package com.srm.master.repo;

import com.srm.master.domain.MaterialSupplierU9;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialSupplierU9Repository extends JpaRepository<MaterialSupplierU9, Long> {

    void deleteByMaterial_Id(Long materialId);

    long countByMaterial_Id(Long materialId);
}
