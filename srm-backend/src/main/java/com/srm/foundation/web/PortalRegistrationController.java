package com.srm.foundation.web;

import com.srm.master.domain.Supplier;
import com.srm.master.domain.SupplierLifecycleStatus;
import com.srm.master.repo.SupplierRepository;
import com.srm.web.error.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PortalRegistration", description = "供应商自助注册")
@RestController
@RequestMapping("/api/v1/public/supplier-registration")
@RequiredArgsConstructor
public class PortalRegistrationController {

    private final SupplierRepository supplierRepository;

    @PostMapping
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest req) {
        if (supplierRepository.existsByCode(req.code())) {
            throw new BadRequestException("供应商编码已存在: " + req.code());
        }

        Supplier s = new Supplier();
        s.setCode(req.code());
        s.setName(req.name());
        s.setContactName(req.contactName());
        s.setContactPhone(req.contactPhone());
        s.setContactEmail(req.contactEmail());
        s.setAddress(req.address());
        s.setBankName(req.bankName());
        s.setBankAccount(req.bankAccount());
        s.setBusinessScope(req.businessScope());
        s.setRegistrationRemark(req.remark());
        s.setLifecycleStatus(SupplierLifecycleStatus.PENDING_REVIEW);

        Supplier saved = supplierRepository.save(s);
        return RegistrationResponse.from(saved);
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 128) String contactName,
            @Size(max = 64) String contactPhone,
            @Size(max = 128) String contactEmail,
            @Size(max = 500) String address,
            @Size(max = 128) String bankName,
            @Size(max = 64) String bankAccount,
            @Size(max = 1000) String businessScope,
            @Size(max = 1000) String remark
    ) {}

    public record RegistrationResponse(
            Long id,
            String code,
            String name,
            String lifecycleStatus,
            String contactName,
            String contactPhone,
            String contactEmail,
            String address,
            String bankName,
            String bankAccount,
            String businessScope,
            String registrationRemark
    ) {
        static RegistrationResponse from(Supplier s) {
            return new RegistrationResponse(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getLifecycleStatus().name(),
                    s.getContactName(),
                    s.getContactPhone(),
                    s.getContactEmail(),
                    s.getAddress(),
                    s.getBankName(),
                    s.getBankAccount(),
                    s.getBusinessScope(),
                    s.getRegistrationRemark());
        }
    }
}
