package com.srm.po.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "po_number_seq")
public class PoNumberSeq {

    @EmbeddedId
    private PoNumberSeqId id;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0;
}
