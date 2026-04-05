package com.srm.foundation.web;

import com.srm.foundation.domain.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseDto(
        Long id,
        Long procurementOrgId,
        String code,
        String name,
        String u9WhCode
) {
    public static WarehouseDto from(Warehouse e) {
        return new WarehouseDto(
                e.getId(),
                e.getProcurementOrg().getId(),
                e.getCode(),
                e.getName(),
                e.getU9WhCode()
        );
    }

    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String u9WhCode
    ) {}
}
