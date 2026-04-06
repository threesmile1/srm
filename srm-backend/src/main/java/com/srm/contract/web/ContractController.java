package com.srm.contract.web;

import com.srm.contract.domain.Contract;
import com.srm.contract.domain.ContractLine;
import com.srm.contract.service.ContractService;
import com.srm.contract.service.ContractService.CreateContractLine;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Contract", description = "合同管理")
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public List<ContractSummaryResponse> list(@RequestParam Long procurementOrgId) {
        return contractService.listByOrg(procurementOrgId).stream()
                .map(ContractSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ContractDetailResponse get(@PathVariable Long id) {
        return ContractDetailResponse.from(contractService.getDetail(id));
    }

    @PostMapping
    public ContractDetailResponse create(@Valid @RequestBody ContractCreateRequest req) {
        List<CreateContractLine> lines = req.lines().stream()
                .map(l -> new CreateContractLine(
                        l.materialId(), l.materialDesc(), l.qty(),
                        l.uom(), l.unitPrice(), l.remark()))
                .toList();
        Contract c = contractService.create(
                req.supplierId(), req.procurementOrgId(), req.title(),
                req.contractType(), req.startDate(), req.endDate(),
                req.currency(), req.remark(), lines);
        return ContractDetailResponse.from(contractService.getDetail(c.getId()));
    }

    @PostMapping("/{id}/activate")
    public ContractDetailResponse activate(@PathVariable Long id) {
        contractService.activate(id);
        return ContractDetailResponse.from(contractService.getDetail(id));
    }

    @PostMapping("/{id}/terminate")
    public ContractDetailResponse terminate(@PathVariable Long id) {
        contractService.terminate(id);
        return ContractDetailResponse.from(contractService.getDetail(id));
    }

    @GetMapping("/expiring")
    public List<ContractSummaryResponse> expiring(
            @RequestParam(required = false) Long procurementOrgId,
            @RequestParam(defaultValue = "30") int daysAhead) {
        return contractService.listExpiring(procurementOrgId, daysAhead).stream()
                .map(ContractSummaryResponse::from)
                .toList();
    }

    // ── DTOs ──

    public record ContractCreateRequest(
            @NotNull Long supplierId,
            @NotNull Long procurementOrgId,
            @NotNull String title,
            String contractType,
            LocalDate startDate,
            LocalDate endDate,
            String currency,
            String remark,
            @NotEmpty List<ContractLineCreateRequest> lines
    ) {}

    public record ContractLineCreateRequest(
            Long materialId,
            String materialDesc,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            String remark
    ) {}

    public record ContractSummaryResponse(
            Long id,
            String contractNo,
            String title,
            String status,
            String contractType,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long procurementOrgId,
            String procurementOrgCode,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalAmount,
            String currency
    ) {
        static ContractSummaryResponse from(Contract c) {
            return new ContractSummaryResponse(
                    c.getId(),
                    c.getContractNo(),
                    c.getTitle(),
                    c.getStatus().name(),
                    c.getContractType(),
                    c.getSupplier().getId(),
                    c.getSupplier().getCode(),
                    c.getSupplier().getName(),
                    c.getProcurementOrg().getId(),
                    c.getProcurementOrg().getCode(),
                    c.getStartDate(),
                    c.getEndDate(),
                    c.getTotalAmount(),
                    c.getCurrency()
            );
        }
    }

    public record ContractDetailResponse(
            Long id,
            String contractNo,
            String title,
            String status,
            String contractType,
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long procurementOrgId,
            String procurementOrgCode,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalAmount,
            String currency,
            String remark,
            List<ContractLineResponse> lines
    ) {
        static ContractDetailResponse from(Contract c) {
            return new ContractDetailResponse(
                    c.getId(),
                    c.getContractNo(),
                    c.getTitle(),
                    c.getStatus().name(),
                    c.getContractType(),
                    c.getSupplier().getId(),
                    c.getSupplier().getCode(),
                    c.getSupplier().getName(),
                    c.getProcurementOrg().getId(),
                    c.getProcurementOrg().getCode(),
                    c.getStartDate(),
                    c.getEndDate(),
                    c.getTotalAmount(),
                    c.getCurrency(),
                    c.getRemark(),
                    c.getLines().stream().map(ContractLineResponse::from).toList()
            );
        }
    }

    public record ContractLineResponse(
            Long id,
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            String materialDesc,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            BigDecimal amount,
            String remark
    ) {
        static ContractLineResponse from(ContractLine line) {
            return new ContractLineResponse(
                    line.getId(),
                    line.getLineNo(),
                    line.getMaterial() != null ? line.getMaterial().getId() : null,
                    line.getMaterial() != null ? line.getMaterial().getCode() : null,
                    line.getMaterial() != null ? line.getMaterial().getName() : null,
                    line.getMaterialDesc(),
                    line.getQty(),
                    line.getUom(),
                    line.getUnitPrice(),
                    line.getAmount(),
                    line.getRemark()
            );
        }
    }
}
