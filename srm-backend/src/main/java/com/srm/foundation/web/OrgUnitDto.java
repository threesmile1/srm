package com.srm.foundation.web;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrgUnitDto(
        Long id,
        Long ledgerId,
        OrgUnitType orgType,
        String code,
        String name,
        String u9OrgCode
) {
    public static OrgUnitDto from(OrgUnit e) {
        return new OrgUnitDto(
                e.getId(),
                e.getLedger().getId(),
                e.getOrgType(),
                e.getCode(),
                e.getName(),
                e.getU9OrgCode()
        );
    }

    public record CreateRequest(
            @NotNull OrgUnitType orgType,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String u9OrgCode
    ) {}
}
