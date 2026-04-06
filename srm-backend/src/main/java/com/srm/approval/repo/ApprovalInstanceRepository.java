package com.srm.approval.repo;

import com.srm.approval.domain.ApprovalInstance;
import com.srm.approval.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, Long> {

    Optional<ApprovalInstance> findByDocTypeAndDocId(String docType, Long docId);

    @EntityGraph(attributePaths = {"steps"})
    Optional<ApprovalInstance> findWithStepsById(Long id);

    List<ApprovalInstance> findByStatusOrderByIdDesc(ApprovalStatus status);

    List<ApprovalInstance> findAllByOrderByIdDesc();
}
