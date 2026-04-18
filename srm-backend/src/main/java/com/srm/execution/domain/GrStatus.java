package com.srm.execution.domain;

/**
 * 收货单业务状态：创建后待客服审批，通过后记入订单实收，驳回则不入账。
 */
public enum GrStatus {
    /** 待审批（未计入采购订单实收） */
    PENDING_APPROVAL,
    /** 已通过 */
    APPROVED,
    /** 已驳回 */
    REJECTED
}
