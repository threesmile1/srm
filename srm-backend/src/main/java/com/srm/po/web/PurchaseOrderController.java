package com.srm.po.web;

import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.integration.u9.U9PurchaseOrderSyncService;
import com.srm.po.service.PoImportService;
import com.srm.po.service.PoImportService.PoImportResult;
import com.srm.po.service.PurchaseOrderService;
import com.srm.po.service.PurchaseOrderService.CreateLine;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "PurchaseOrder", description = "A3 采购订单")
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final PoImportService poImportService;
    private final U9PurchaseOrderSyncService u9PurchaseOrderSyncService;

    @GetMapping
    public List<PoSummaryResponse> list(@RequestParam Long procurementOrgId) {
        return purchaseOrderService.listByOrg(procurementOrgId).stream()
                .map(PoSummaryResponse::from)
                .toList();
    }

    @GetMapping("/paged")
    public Page<PoSummaryResponse> listPaged(
            @RequestParam Long procurementOrgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return purchaseOrderService.pageByOrg(procurementOrgId, page, size)
                .map(PoSummaryResponse::from);
    }

    @GetMapping("/{id:\\d+}")
    public PoDetailResponse get(@PathVariable Long id) {
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    @PostMapping
    public PoDetailResponse create(@Valid @RequestBody PoCreateRequest req) {
        List<CreateLine> lines = req.lines().stream()
                .map(l -> new CreateLine(
                        l.materialId(),
                        l.warehouseId(),
                        l.qty(),
                        l.uom(),
                        l.unitPrice(),
                        l.requestedDate()))
                .toList();
        PurchaseOrder po = purchaseOrderService.create(
                req.procurementOrgId(),
                req.supplierId(),
                req.currency(),
                req.remark(),
                lines);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(po.getId()));
    }

    @PostMapping("/import")
    public PoImportResult importOrders(@RequestParam("file") MultipartFile file) {
        return poImportService.importOrders(file);
    }

    /**
     * 从帆软拉取 U9 已审核未关闭采购订单（{@code srm.u9.purchase-order-report-path}，默认 API/caigou_cp.cpt），
     * 写入 SRM 并自动发布给供应商（幂等键：采购组织 + U9 单据编号）。
     */
    @PostMapping("/sync-from-u9")
    public U9PurchaseOrderSyncService.U9PurchaseOrderSyncResult syncPurchaseOrdersFromU9() {
        return u9PurchaseOrderSyncService.fetchAndApply();
    }

    @PostMapping("/{id:\\d+}/submit")
    public PoDetailResponse submit(@PathVariable Long id) {
        purchaseOrderService.submitForApproval(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    @PostMapping("/{id:\\d+}/approve")
    public PoDetailResponse approve(@PathVariable Long id) {
        purchaseOrderService.approve(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    @PostMapping("/{id:\\d+}/release")
    public PoDetailResponse release(@PathVariable Long id) {
        purchaseOrderService.release(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    @PostMapping("/{id:\\d+}/cancel")
    public PoDetailResponse cancel(@PathVariable Long id) {
        purchaseOrderService.cancel(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    @PostMapping("/{id:\\d+}/close")
    public PoDetailResponse close(@PathVariable Long id) {
        purchaseOrderService.close(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    /**
     * 误操作兜底：仅在订单无收货且当前为 CLOSED 时，允许恢复为 RELEASED（便于继续收货/协同）。
     * 仅限管理员使用；后续可替换为更严谨的审批/工单流程。
     */
    @PostMapping("/{id:\\d+}/reopen")
    public PoDetailResponse reopen(@PathVariable Long id) {
        purchaseOrderService.reopenIfNoReceipt(id);
        return PoDetailResponse.from(purchaseOrderService.requireDetail(id));
    }

    public record PoCreateRequest(
            @NotNull Long procurementOrgId,
            @NotNull Long supplierId,
            String currency,
            String remark,
            @NotEmpty List<PoLineCreateRequest> lines
    ) {}

    public record PoLineCreateRequest(
            @NotNull Long materialId,
            @NotNull Long warehouseId,
            @NotNull BigDecimal qty,
            String uom,
            @NotNull BigDecimal unitPrice,
            LocalDate requestedDate
    ) {}

    public record PoSummaryResponse(
            Long id,
            String poNo,
            String u9DocNo,
            String officialOrderNo,
            String releasedAt,
            String status,
            String supplierCode,
            String supplierName,
            String currency,
            String exportStatus
    ) {
        static PoSummaryResponse from(PurchaseOrder po) {
            return new PoSummaryResponse(
                    po.getId(),
                    po.getPoNo(),
                    po.getU9DocNo(),
                    po.getU9OfficialOrderNo(),
                    po.getReleasedAt() != null ? po.getReleasedAt().toString() : null,
                    po.getStatus().name(),
                    po.getSupplier().getCode(),
                    po.getSupplier().getName(),
                    po.getCurrency(),
                    po.getExportStatus().name()
            );
        }
    }

    public record PoDetailResponse(
            Long id,
            String poNo,
            String status,
            Long procurementOrgId,
            String procurementOrgCode,
            Long supplierId,
            String supplierCode,
            String currency,
            int revisionNo,
            String remark,
            String exportStatus,
            List<PoLineResponse> lines
    ) {
        static PoDetailResponse from(PurchaseOrder po) {
            return new PoDetailResponse(
                    po.getId(),
                    po.getPoNo(),
                    po.getStatus().name(),
                    po.getProcurementOrg().getId(),
                    po.getProcurementOrg().getCode(),
                    po.getSupplier().getId(),
                    po.getSupplier().getCode(),
                    po.getCurrency(),
                    po.getRevisionNo(),
                    po.getRemark(),
                    po.getExportStatus().name(),
                    po.getLines().stream().map(PoLineResponse::from).toList()
            );
        }
    }

    public record PoLineResponse(
            Long id,
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            String materialSpec,
            BigDecimal qty,
            BigDecimal receivedQty,
            String uom,
            BigDecimal unitPrice,
            BigDecimal amount,
            LocalDate requestedDate,
            Long warehouseId,
            String warehouseCode,
            BigDecimal confirmedQty,
            LocalDate promisedDate,
            String supplierRemark,
            String confirmedAt
    ) {
        static PoLineResponse from(PurchaseOrderLine line) {
            return new PoLineResponse(
                    line.getId(),
                    line.getLineNo(),
                    line.getMaterial().getId(),
                    line.getMaterial().getCode(),
                    line.getMaterial().getName(),
                    line.getMaterial().getSpecification(),
                    line.getQty(),
                    line.getReceivedQty(),
                    line.getUom(),
                    line.getUnitPrice(),
                    line.getAmount(),
                    line.getRequestedDate(),
                    line.getWarehouse().getId(),
                    line.getWarehouse().getCode(),
                    line.getConfirmedQty(),
                    line.getPromisedDate(),
                    line.getSupplierRemark(),
                    line.getConfirmedAt() != null ? line.getConfirmedAt().toString() : null
            );
        }
    }
}
