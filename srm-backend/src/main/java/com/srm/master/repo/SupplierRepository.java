package com.srm.master.repo;

import com.srm.master.domain.Supplier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"authorizedProcurementOrgs"})
    @Override
    List<Supplier> findAll();

    @EntityGraph(attributePaths = {"authorizedProcurementOrgs"})
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> fetchWithOrgs(@Param("id") Long id);
}
