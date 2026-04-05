package com.srm.foundation.service;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.LedgerRepository;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoundationService {

    private final LedgerRepository ledgerRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;

    public List<Ledger> listLedgers() {
        return ledgerRepository.findAll();
    }

    @Transactional
    public Ledger createLedger(String code, String name, String u9LedgerCode) {
        if (ledgerRepository.existsByCode(code)) {
            throw new BadRequestException("账套编码已存在: " + code);
        }
        Ledger ledger = new Ledger();
        ledger.setCode(code);
        ledger.setName(name);
        ledger.setU9LedgerCode(u9LedgerCode);
        return ledgerRepository.save(ledger);
    }

    public Ledger requireLedger(Long id) {
        return ledgerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("账套不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> listOrgUnits(Long ledgerId) {
        Ledger ledger = requireLedger(ledgerId);
        return orgUnitRepository.findByLedgerOrderByCodeAsc(ledger);
    }

    @Transactional
    public OrgUnit createOrgUnit(Long ledgerId, OrgUnitType type, String code, String name, String u9OrgCode) {
        Ledger ledger = requireLedger(ledgerId);
        if (orgUnitRepository.existsByLedgerAndCode(ledger, code)) {
            throw new BadRequestException("该账套下组织编码已存在: " + code);
        }
        OrgUnit ou = new OrgUnit();
        ou.setLedger(ledger);
        ou.setOrgType(type);
        ou.setCode(code);
        ou.setName(name);
        ou.setU9OrgCode(u9OrgCode);
        return orgUnitRepository.save(ou);
    }

    public OrgUnit requireOrgUnit(Long id) {
        return orgUnitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("组织不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<Warehouse> listWarehouses(Long procurementOrgId) {
        OrgUnit org = requireOrgUnit(procurementOrgId);
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("仅可查询采购组织下的仓库，当前组织类型: " + org.getOrgType());
        }
        return warehouseRepository.findByProcurementOrgOrderByCodeAsc(org);
    }

    @Transactional
    public Warehouse createWarehouse(Long procurementOrgId, String code, String name, String u9WhCode) {
        OrgUnit org = requireOrgUnit(procurementOrgId);
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("仓库必须挂在采购组织下，当前组织类型: " + org.getOrgType());
        }
        if (warehouseRepository.existsByProcurementOrgAndCode(org, code)) {
            throw new BadRequestException("该采购组织下仓库编码已存在: " + code);
        }
        Warehouse w = new Warehouse();
        w.setProcurementOrg(org);
        w.setCode(code);
        w.setName(name);
        w.setU9WhCode(u9WhCode);
        return warehouseRepository.save(w);
    }
}
