package com.srm.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "gr_number_seq")
public class GrNumberSeq {

    @EmbeddedId
    private GrNumberSeqId id;

    @Column(name = "seq_value", nullable = false)
    private long seqValue;
}
