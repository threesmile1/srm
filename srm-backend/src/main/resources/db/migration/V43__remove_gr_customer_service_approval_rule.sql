-- 收货单不再走客服审批：移除历史 GR 审批规则（ASN 等其它规则保留）
DELETE FROM approval_rule WHERE doc_type = 'GR';
