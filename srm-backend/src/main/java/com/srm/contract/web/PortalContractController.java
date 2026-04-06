package com.srm.contract.web;

import com.srm.contract.domain.Contract;
import com.srm.contract.repo.ContractRepository;
import com.srm.foundation.web.AuthController;
import com.srm.web.error.ForbiddenException;
import com.srm.web.error.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PortalContract", description = "供应商门户 - 合同只读")
@RestController
@RequestMapping("/api/v1/portal/contracts")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalContractController {

    private final ContractRepository contractRepository;

    private static long requireSupplierId(HttpSession session) {
        Long sid = (Long) session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid == null) {
            throw new ForbiddenException("当前用户非供应商账号");
        }
        return sid;
    }

    @GetMapping
    public List<ContractController.ContractSummaryResponse> list(HttpSession session) {
        long sid = requireSupplierId(session);
        return contractRepository.findBySupplierIdOrderByIdDesc(sid).stream()
                .map(ContractController.ContractSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ContractController.ContractDetailResponse get(@PathVariable Long id, HttpSession session) {
        long sid = requireSupplierId(session);
        Contract c = contractRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("合同不存在: " + id));
        if (!c.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看该合同");
        }
        return ContractController.ContractDetailResponse.from(c);
    }
}
