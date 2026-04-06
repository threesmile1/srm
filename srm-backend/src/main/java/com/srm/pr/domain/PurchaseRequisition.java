package com.srm.pr.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "purchase_requisition")
public class PurchaseRequisition extends BaseEntity {

    @Column(name = "pr_no", nullable = false, length = 64, unique = true)
    private String prNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @Column(name = "requester_name", length = 128)
    private String requesterName;

    @Column(length = 128)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PrStatus status = PrStatus.DRAFT;

    @Column(length = 2000)
    private String remark;

    @OneToMany(mappedBy = "purchaseRequisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseRequisitionLine> lines = new ArrayList<>();
}
