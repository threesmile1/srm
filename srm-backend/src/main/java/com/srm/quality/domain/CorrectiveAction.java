package com.srm.quality.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "corrective_action")
public class CorrectiveAction extends BaseEntity {

    @Column(name = "ca_no", nullable = false, unique = true, length = 64)
    private String caNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id")
    private QualityInspection inspection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @Column(name = "issue_description", nullable = false, length = 2000)
    private String issueDescription;

    @Column(name = "root_cause", length = 2000)
    private String rootCause;

    @Column(name = "corrective_measures", length = 2000)
    private String correctiveMeasures;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 32)
    private String status = "OPEN"; // OPEN, IN_PROGRESS, CLOSED, OVERDUE

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(length = 1000)
    private String remark;
}
