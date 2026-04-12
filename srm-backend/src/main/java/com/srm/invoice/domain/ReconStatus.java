package com.srm.invoice.domain;

/**
 * 对账状态（甄云类双入口）：
 * <ul>
 *   <li>采购生成对账单 → 先 {@link #PENDING_SUPPLIER}，供方确认后 → {@link #PENDING_PROCUREMENT}</li>
 *   <li>供应商发起对账 → 直接 {@link #PENDING_PROCUREMENT}，采购核对后确认/驳回</li>
 * </ul>
 */
public enum ReconStatus {
    /** 待供应商确认（通常为采购侧生成对账单后） */
    PENDING_SUPPLIER,
    /** 待采购确认（供应商已确认采购单，或供应商发起对账后待采购核对） */
    PENDING_PROCUREMENT,
    /** 双方已确认 */
    CONFIRMED,
    /** 争议/例外（保留扩展） */
    DISPUTED,
}
