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
@Table(name = "asn_number_seq")
public class AsnNumberSeq {

    @EmbeddedId
    private AsnNumberSeqId id;

    @Column(name = "seq_value", nullable = false)
    private long seqValue;
}
