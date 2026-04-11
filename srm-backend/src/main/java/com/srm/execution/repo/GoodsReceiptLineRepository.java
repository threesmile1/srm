package com.srm.execution.repo;

import com.srm.execution.domain.GoodsReceiptLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    /** 至少一行关联 ASN（发货通知）的收货单 id */
    @Query("select distinct gl.goodsReceipt.id from GoodsReceiptLine gl where gl.goodsReceipt.id in :grIds and gl.asnLine is not null")
    List<Long> findGoodsReceiptIdsHavingAsnLine(@Param("grIds") Collection<Long> grIds);

    @Query("select count(gl) from GoodsReceiptLine gl join gl.asnLine al where al.asnNotice.id = :asnNoticeId")
    long countByAsnNoticeId(@Param("asnNoticeId") Long asnNoticeId);

    @EntityGraph(attributePaths = {"purchaseOrderLine", "goodsReceipt"})
    @Query("select gl from GoodsReceiptLine gl where gl.goodsReceipt.purchaseOrder.id = :poId and gl.asnLine is null")
    List<GoodsReceiptLine> findForPurchaseOrderWithNullAsnLine(@Param("poId") Long purchaseOrderId);

    @EntityGraph(attributePaths = {"purchaseOrderLine", "goodsReceipt"})
    @Query("select gl from GoodsReceiptLine gl where gl.goodsReceipt.purchaseOrder.id = :poId")
    List<GoodsReceiptLine> findForPurchaseOrder(@Param("poId") Long purchaseOrderId);
}
