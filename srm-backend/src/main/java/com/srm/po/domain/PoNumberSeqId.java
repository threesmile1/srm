package com.srm.po.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PoNumberSeqId implements Serializable {

    @Column(name = "procurement_org_id", nullable = false)
    private Long procurementOrgId;

    @Column(name = "year_val", nullable = false)
    private Integer yearVal;
}
