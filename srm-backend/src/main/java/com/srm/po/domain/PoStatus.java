package com.srm.po.domain;

public enum PoStatus {
    /** 草稿 */
    DRAFT,
    /** 已审核（可发布） */
    APPROVED,
    /** 已发布供应商 */
    RELEASED,
    /** 已关闭 */
    CLOSED,
    /** 已取消 */
    CANCELLED
}
