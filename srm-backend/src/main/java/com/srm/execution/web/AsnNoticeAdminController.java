package com.srm.execution.web;

import com.srm.execution.service.AsnService;
import com.srm.web.error.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AsnNotice", description = "A5 发货通知（管理端）")
@RestController
@RequestMapping("/api/v1/purchase-orders/{purchaseOrderId}/asn-notices")
@RequiredArgsConstructor
public class AsnNoticeAdminController {

    private final AsnService asnService;

    @GetMapping
    public List<AsnNoticeResponse> list(@PathVariable Long purchaseOrderId) {
        return asnService.listByPurchaseOrder(purchaseOrderId).stream()
                .map(AsnNoticeResponse::from)
                .toList();
    }

    @GetMapping("/{asnId}")
    public AsnNoticeResponse get(
            @PathVariable Long purchaseOrderId,
            @PathVariable Long asnId
    ) {
        var n = asnService.requireWithLines(asnId);
        if (!n.getPurchaseOrder().getId().equals(purchaseOrderId)) {
            throw new BadRequestException("ASN 不属于该订单");
        }
        return AsnNoticeResponse.from(n);
    }
}
