package com.srm.master.repo;

import com.srm.master.domain.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MaterialItemRepository extends JpaRepository<MaterialItem, Long> {

    @Query("select m.code from MaterialItem m order by m.code asc")
    List<String> findAllCodesOrderByCode();

    Optional<MaterialItem> findByCode(String code);

    Optional<MaterialItem> findByCodeIgnoreCase(String code);

    Optional<MaterialItem> findFirstByU9ItemCode(String u9ItemCode);

    Optional<MaterialItem> findFirstByU9ItemCodeIgnoreCase(String u9ItemCode);

    boolean existsByCode(String code);
}
