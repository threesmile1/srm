package com.srm.invoice.domain;

/** 对账状态：甄云类两步确认 — 供应商确认 → 采购确认 */
public enum ReconStatus {
    /** 待供应商确认 */
    PENDING_SUPPLIER,
    /** 待采购确认（供应商已确认） */
    PENDING_PROCUREMENT,
    /** 双方已确认 */
    CONFIRMED,
    /** 争议/例外（保留扩展） */
    DISPUTED,
}
