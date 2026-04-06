package com.srm.approval.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "approval_instance")
public class ApprovalInstance extends BaseEntity {

    @Column(name = "doc_type", nullable = false, length = 32)
    private String docType;

    @Column(name = "doc_id", nullable = false)
    private Long docId;

    @Column(name = "doc_no", length = 64)
    private String docNo;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepLevel ASC, id ASC")
    private List<ApprovalStep> steps = new ArrayList<>();
}
