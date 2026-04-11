package com.srm.execution.domain;

public enum AsnStatus {
    /** 已提交，计入可发货占用 */
    SUBMITTED,
    /** 供应商作废，不再占用可发货量 */
    CANCELLED
}
