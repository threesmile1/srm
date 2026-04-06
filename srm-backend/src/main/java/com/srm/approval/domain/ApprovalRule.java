package com.srm.approval.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Entity
@Table(name = "approval_rule")
public class ApprovalRule extends BaseEntity {

    @Column(name = "doc_type", nullable = false, length = 32)
    private String docType;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "approval_level", nullable = false)
    private int approvalLevel = 1;

    @Column(name = "approver_role", nullable = false, length = 64)
    private String approverRole;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;
}
