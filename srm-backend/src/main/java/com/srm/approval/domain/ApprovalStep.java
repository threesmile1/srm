package com.srm.approval.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity
@Table(name = "approval_step")
public class ApprovalStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false)
    private ApprovalInstance instance;

    @Column(name = "step_level", nullable = false)
    private int stepLevel;

    @Column(name = "approver_role", nullable = false, length = 64)
    private String approverRole;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_name", length = 128)
    private String approverName;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StepAction action;

    @Column(length = 1000)
    private String comment;

    @Column(name = "acted_at")
    private Instant actedAt;
}
