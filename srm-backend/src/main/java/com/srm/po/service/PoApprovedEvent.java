package com.srm.po.service;

/**
 * PO 审批通过事件：用于触发「自动发布」等后置动作，避免在审批回调中引入 service 依赖造成循环引用。
 */
public record PoApprovedEvent(Long poId, String poNo) {}

