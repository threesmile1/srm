package com.srm.master.repo;

import com.srm.master.domain.MaterialItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            "SELECT m FROM MaterialItem m WHERE LOWER(m.code) LIKE LOWER(CONCAT('%', :q, '%'))"
                    + " OR LOWER(COALESCE(m.name, '')) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<MaterialItem> searchByCodeOrName(@Param("q") String q, Pageable pageable);
}
