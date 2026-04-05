package com.srm.foundation.web;

import com.srm.foundation.domain.Ledger;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LedgerDto(
        Long id,
        String code,
        String name,
        String u9LedgerCode
) {
    public static LedgerDto from(Ledger e) {
        return new LedgerDto(e.getId(), e.getCode(), e.getName(), e.getU9LedgerCode());
    }

    public record CreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String u9LedgerCode
    ) {}
}
