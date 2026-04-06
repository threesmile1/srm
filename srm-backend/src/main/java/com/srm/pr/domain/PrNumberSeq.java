package com.srm.pr.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "pr_number_seq")
@IdClass(PrNumberSeqId.class)
public class PrNumberSeq {

    @Id
    @Column(name = "procurement_org_id")
    private Long procurementOrgId;

    @Id
    @Column(name = "year_val")
    private int yearVal;

    @Column(name = "seq_value", nullable = false)
    private long seqValue;
}
