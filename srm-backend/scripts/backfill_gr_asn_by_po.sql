-- 按采购订单补全 goods_receipt_line.asn_line_id（仅 NULL 行）
-- 规则：每条 purchase_order_line 取该 PO 下 asn_notice.id 最大的一份通知里对应的 asn_line。
-- 使用前将 @po_id 设为 purchase_order.id，先在测试库执行 SELECT 核对再 UPDATE。
--
-- 推荐优先使用 API：POST /api/v1/goods-receipts/backfill-asn
-- Body: {"purchaseOrderId": <id>, "overwriteExisting": false}

SET @po_id = 0;

UPDATE goods_receipt_line grl
INNER JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
INNER JOIN (
    SELECT al.id AS asn_line_id, al.purchase_order_line_id
    FROM asn_line al
    INNER JOIN asn_notice an ON an.id = al.asn_notice_id
    INNER JOIN (
        SELECT al2.purchase_order_line_id, MAX(an2.id) AS max_notice_id
        FROM asn_line al2
        INNER JOIN asn_notice an2 ON an2.id = al2.asn_notice_id
        WHERE an2.purchase_order_id = @po_id
        GROUP BY al2.purchase_order_line_id
    ) t ON t.purchase_order_line_id = al.purchase_order_line_id AND an.id = t.max_notice_id
) m ON m.purchase_order_line_id = grl.purchase_order_line_id
SET grl.asn_line_id = m.asn_line_id
WHERE gr.purchase_order_id = @po_id
  AND grl.asn_line_id IS NULL;
