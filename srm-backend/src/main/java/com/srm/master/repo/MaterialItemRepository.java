package com.srm.master.repo;

import com.srm.master.domain.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialItemRepository extends JpaRepository<MaterialItem, Long> {

    Optional<MaterialItem> findByCode(String code);

    boolean existsByCode(String code);
}
