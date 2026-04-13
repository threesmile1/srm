package com.srm.pr.web;

import com.srm.po.domain.PurchaseOrder;
import com.srm.pr.domain.PrStatus;
import com.srm.pr.domain.PurchaseRequisition;
import com.srm.pr.domain.PurchaseRequisitionLine;
import com.srm.pr.service.PurchaseRequisitionService;
import com.srm.pr.service.PurchaseRequisitionService.ConvertPrLineCmd;
import com.srm.pr.service.PurchaseRequisitionService.CreatePrLine;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "PurchaseRequisition", description = "请购单")
@RestController
@RequestMapping("/api/v1/purchase-requisitions")
@RequiredArgsConstructor
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService prService;

    @GetMapping
    public List<PrSummaryResponse> list(@RequestParam Long procurementOrgId) {
        return prService.listByOrg(procurementOrgId).stream()
                .map(PrSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PrDetailResponse get(@PathVariable Long id) {
        return PrDetailResponse.from(prService.requireDetail(id));
    }

    @PostMapping
    public PrDetailResponse create(@Valid @RequestBody PrCreateRequest req) {
        List<CreatePrLine> lines = req.lines().stream()
                .map(l -> new CreatePrLine(l.materialId(), l.warehouseId(), l.supplierId(),
                        l.qty(), l.uom(), l.unitPrice(), l.requestedDate(), l.remark()))
                .toList();
        PurchaseRequisition pr = prService.create(
                req.procurementOrgId(), req.requesterName(), req.department(), req.remark(), lines);
        return PrDetailResponse.from(prService.requireDetail(pr.getId()));
    }

    @PostMapping("/{id}/submit")
    public PrDetailResponse submit(@PathVariable Long id) {
        prService.submit(id);
        return PrDetailResponse.from(prService.requireDetail(id));
    }

    @PostMapping("/{id}/approve")
    public PrDetailResponse approve(@PathVariable Long id) {
        prService.approve(id);
        return PrDetailResponse.from(prService.requireDetail(id));
    }

    @PostMapping("/{id}/reject")
    public PrDetailResponse reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest req) {
        prService.reject(id, req != null ? req.reason() : null);
        return PrDetailResponse.from(prService.requireDetail(id));
    }

    @PostMapping("/{id}/cancel")
    public PrDetailResponse cancel(@PathVariable Long id) {
        prService.cancel(id);
        return PrDetailResponse.from(prService.requireDetail(id));
    }

    @PostMapping("/{id}/convert-to-po")
    public List<ConvertResult> convertToPo(@PathVariable Long id, @Valid @RequestBody ConvertRequest req) {
        List<ConvertPrLineCmd> cmds = req.lines().stream()
                .map(l -> new ConvertPrLineCmd(l.lineId(), l.supplierId(), l.unitPrice(), l.requestedDate()))
                .toList();
        List<PurchaseOrder> pos = prService.convertToPo(id, cmds);
        return pos.stream().map(po -> new ConvertResult(po.getId(), po.getPoNo())).toList();
    }

    // --- DTOs ---

    public record PrCreateRequest(
            @NotNull Long procurementOrgId,
            String requesterName,
            String department,
            String remark,
            @NotEmpty List<PrLineCreateRequest> lines
    ) {}

    public record PrLineCreateRequest(
            @NotNull Long materialId,
            Long warehouseId,
            Long supplierId,
            @NotNull BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            LocalDate requestedDate,
            String remark
    ) {}

    public record RejectRequest(String reason) {}

    public record ConvertRequest(@NotEmpty List<@Valid ConvertLineRequest> lines) {}

    public record ConvertLineRequest(
            @NotNull Long lineId,
            @NotNull Long supplierId,
            @NotNull @Positive BigDecimal unitPrice,
            LocalDate requestedDate
    ) {}

    public record ConvertResult(Long poId, String poNo) {}

    public record PrSummaryResponse(
            Long id, String prNo, String status, String requesterName,
            String department, String procurementOrgCode
    ) {
        static PrSummaryResponse from(PurchaseRequisition pr) {
            return new PrSummaryResponse(pr.getId(), pr.getPrNo(), pr.getStatus().name(),
                    pr.getRequesterName(), pr.getDepartment(), pr.getProcurementOrg().getCode());
        }
    }

    public record PrDetailResponse(
            Long id, String prNo, String status, Long procurementOrgId,
            String procurementOrgCode, String requesterName, String department,
            String remark, List<PrLineResponse> lines
    ) {
        static PrDetailResponse from(PurchaseRequisition pr) {
            return new PrDetailResponse(
                    pr.getId(), pr.getPrNo(), pr.getStatus().name(),
                    pr.getProcurementOrg().getId(), pr.getProcurementOrg().getCode(),
                    pr.getRequesterName(), pr.getDepartment(), pr.getRemark(),
                    pr.getLines().stream().map(PrLineResponse::from).toList());
        }
    }

    public record PrLineResponse(
            Long id, int lineNo, Long materialId, String materialCode, String materialName,
            BigDecimal qty, String uom, BigDecimal unitPrice, LocalDate requestedDate,
            Long warehouseId, String warehouseCode, Long supplierId, String supplierCode,
            String remark, Long convertedPoId, String convertedPoNo
    ) {
        static PrLineResponse from(PurchaseRequisitionLine l) {
            return new PrLineResponse(
                    l.getId(), l.getLineNo(),
                    l.getMaterial().getId(), l.getMaterial().getCode(), l.getMaterial().getName(),
                    l.getQty(), l.getUom(), l.getUnitPrice(), l.getRequestedDate(),
                    l.getWarehouse() != null ? l.getWarehouse().getId() : null,
                    l.getWarehouse() != null ? l.getWarehouse().getCode() : null,
                    l.getSupplier() != null ? l.getSupplier().getId() : null,
                    l.getSupplier() != null ? l.getSupplier().getCode() : null,
                    l.getRemark(),
                    l.getConvertedPo() != null ? l.getConvertedPo().getId() : null,
                    l.getConvertedPo() != null ? l.getConvertedPo().getPoNo() : null);
        }
    }
}
