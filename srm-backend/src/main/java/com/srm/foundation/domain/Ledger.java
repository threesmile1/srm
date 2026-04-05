package com.srm.foundation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ledger")
public class Ledger extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    /** 与 U9 账套编码对齐 */
    @Column(name = "u9_ledger_code", length = 64)
    private String u9LedgerCode;
}
