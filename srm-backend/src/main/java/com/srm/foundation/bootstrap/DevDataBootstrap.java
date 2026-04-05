package com.srm.foundation.bootstrap;

import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Role;
import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.LedgerRepository;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.RoleRepository;
import com.srm.foundation.repo.UserAccountRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.master.domain.Supplier;
import com.srm.master.repo.SupplierRepository;
import com.srm.master.service.MasterDataService;
import com.srm.po.service.PurchaseOrderService;
import com.srm.po.service.PurchaseOrderService.CreateLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * dev 环境示例数据：账套/组织/仓库/用户 → 主数据 → 演示采购订单（已发布）。
 */
@Slf4j
@Component
@Order(1)
@Profile("dev")
@RequiredArgsConstructor
public class DevDataBootstrap implements ApplicationRunner {

    private final LedgerRepository ledgerRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final WarehouseRepository warehouseRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupplierRepository supplierRepository;
    private final MasterDataService masterDataService;
    private final PurchaseOrderService purchaseOrderService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ledgerRepository.count() == 0) {
            seedFoundation();
        }
        if (supplierRepository.count() == 0) {
            seedMasterAndDemoPo();
        }
    }

    private void seedFoundation() {
        log.info("Seeding dev foundation (ledger/orgs/warehouses/admin)...");

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setName("系统管理员");
        roleRepository.save(adminRole);

        Ledger ledger = new Ledger();
        ledger.setCode("LEDGER01");
        ledger.setName("演示账套");
        ledger.setU9LedgerCode("U9-L01");
        ledgerRepository.save(ledger);

        String[][] factories = {
                {"P01", "一厂采购组织", "ORG-P01"},
                {"P02", "二厂采购组织", "ORG-P02"},
                {"P03", "三厂采购组织", "ORG-P03"},
                {"P04", "四厂采购组织", "ORG-P04"},
        };
        OrgUnit firstProc = null;
        for (String[] f : factories) {
            OrgUnit ou = new OrgUnit();
            ou.setLedger(ledger);
            ou.setOrgType(OrgUnitType.PROCUREMENT);
            ou.setCode(f[0]);
            ou.setName(f[1]);
            ou.setU9OrgCode(f[2]);
            orgUnitRepository.save(ou);
            if (firstProc == null) {
                firstProc = ou;
            }
            Warehouse wh = new Warehouse();
            wh.setProcurementOrg(ou);
            wh.setCode("WH-" + f[0]);
            wh.setName("主仓库-" + f[0]);
            wh.setU9WhCode("U9-WH-" + f[0]);
            warehouseRepository.save(wh);
        }

        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setDisplayName("管理员");
        admin.setEnabled(true);
        admin.setDefaultProcurementOrg(firstProc);
        admin.getRoles().add(adminRole);
        userAccountRepository.save(admin);

        log.info("Foundation seed done. admin / admin123");
    }

    private void seedMasterAndDemoPo() {
        log.info("Seeding dev master data + demo PO...");

        Ledger ledger = ledgerRepository.findByCode("LEDGER01").orElseThrow();
        List<OrgUnit> procs = orgUnitRepository.findByLedgerAndOrgTypeOrderByCodeAsc(ledger, OrgUnitType.PROCUREMENT);
        Set<Long> procIds = procs.stream().map(OrgUnit::getId).collect(Collectors.toSet());
        OrgUnit p01 = procs.stream().filter(o -> "P01".equals(o.getCode())).findFirst().orElseThrow();
        Warehouse whP01 = warehouseRepository.findByProcurementOrgOrderByCodeAsc(p01).get(0);

        Supplier supplier = masterDataService.createSupplier(
                "S001",
                "演示供应商",
                "U9-V-S001",
                null,
                procIds
        );

        var m1 = masterDataService.createMaterial("M001", "演示物料A", "PCS", "U9-M001");
        masterDataService.createMaterial("M002", "演示物料B", "KG", "U9-M002");

        UserAccount portal = new UserAccount();
        portal.setUsername("portal");
        portal.setPasswordHash(passwordEncoder.encode("portal123"));
        portal.setDisplayName("供应商门户演示");
        portal.setEnabled(true);
        portal.setSupplier(supplier);
        userAccountRepository.save(portal);

        var po = purchaseOrderService.create(
                p01.getId(),
                supplier.getId(),
                "CNY",
                "dev 演示订单",
                List.of(new CreateLine(
                        m1.getId(),
                        whP01.getId(),
                        new BigDecimal("100"),
                        "PCS",
                        new BigDecimal("12.50"),
                        null
                ))
        );
        purchaseOrderService.approve(po.getId());
        purchaseOrderService.release(po.getId());

        log.info("Master + PO seed done. portal / portal123 ; supplierId={} ; demo PO {}", supplier.getId(), po.getId());
    }
}
