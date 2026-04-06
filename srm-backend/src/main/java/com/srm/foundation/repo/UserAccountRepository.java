package com.srm.foundation.repo;

import com.srm.foundation.domain.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = {"roles", "defaultProcurementOrg", "supplier"})
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "defaultProcurementOrg"})
    List<UserAccount> findAllByOrderByUsernameAsc();

    boolean existsByUsername(String username);

    @Query("select distinct u from UserAccount u join u.roles r where r.code = :roleCode "
            + "and u.enabled = true and u.supplier is null")
    List<UserAccount> findInternalUsersByRoleCode(@Param("roleCode") String roleCode);

    /** 采购组织侧常见岗位（含管理员），用于发票/收货/供应商确认等广播 */
    @Query("select distinct u from UserAccount u join u.roles r where u.defaultProcurementOrg.id = :orgId "
            + "and u.enabled = true and u.supplier is null "
            + "and r.code in ('ADMIN','BUYER','BUYER_MANAGER','WAREHOUSE')")
    List<UserAccount> findInternalStakeholdersByOrg(@Param("orgId") Long orgId);
}
