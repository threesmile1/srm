package com.srm.approval.repo;

import com.srm.approval.domain.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {

    List<ApprovalRule> findByDocTypeAndEnabledTrueOrderByMinAmountAscApprovalLevelAsc(String docType);

    List<ApprovalRule> findAllByOrderByDocTypeAscMinAmountAscApprovalLevelAsc();
}
