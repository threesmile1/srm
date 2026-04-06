package com.srm.pr.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PrNumberSeqId implements Serializable {
    private Long procurementOrgId;
    private int yearVal;
}
