package com.srm.contract.service;

import com.srm.contract.domain.Contract;
import com.srm.contract.domain.ContractLine;
import com.srm.contract.domain.ContractStatus;
import com.srm.contract.repo.ContractRepository;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.service.AuditService;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final MasterDataService masterDataService;
    private final OrgUnitRepository orgUnitRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    @Transactional(readOnly = true)
    public List<Contract> listByOrg(Long orgId) {
        return contractRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional(readOnly = true)
    public Contract getDetail(Long id) {
        return contractRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("合同不存在: " + id));
    }

    @Transactional
    public Contract create(Long supplierId, Long orgId, String title, String contractType,
                           LocalDate startDate, LocalDate endDate, String currency,
                           String remark, List<CreateContractLine> lines) {
        OrgUnit org = orgUnitRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + orgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        masterDataService.assertSupplierAuthorizedForOrg(supplier, org);

        Contract c = new Contract();
        c.setContractNo(generateContractNo());
        c.setTitle(title);
        c.setSupplier(supplier);
        c.setProcurementOrg(org);
        c.setContractType(contractType != null && !contractType.isBlank() ? contractType : "FRAMEWORK");
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        c.setCurrency(currency != null && !currency.isBlank() ? currency : "CNY");
        c.setRemark(remark);

        BigDecimal total = BigDecimal.ZERO;
        int n = 1;
        for (CreateContractLine cl : lines) {
            ContractLine line = new ContractLine();
            line.setContract(c);
            line.setLineNo(n++);
            if (cl.materialId() != null) {
                MaterialItem mat = masterDataService.requireMaterial(cl.materialId());
                line.setMaterial(mat);
            }
            line.setMaterialDesc(cl.materialDesc());
            line.setQty(cl.qty());
            line.setUom(cl.uom());
            line.setUnitPrice(cl.unitPrice());
            BigDecimal amount = BigDecimal.ZERO;
            if (cl.qty() != null && cl.unitPrice() != null) {
                amount = cl.qty().multiply(cl.unitPrice()).setScale(4, RoundingMode.HALF_UP);
            }
            line.setAmount(amount);
            line.setRemark(cl.remark());
            total = total.add(amount);
            c.getLines().add(line);
        }
        c.setTotalAmount(total);

        Contract saved = contractRepository.save(c);
        auditService.log(null, null, "CREATE_CONTRACT", "CONTRACT", saved.getId(),
                "contractNo=" + saved.getContractNo() + " lines=" + saved.getLines().size(), null);
        return saved;
    }

    @Transactional
    public Contract activate(Long id) {
        Contract c = getDetail(id);
        if (c.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException("仅草稿合同可激活，当前状态: " + c.getStatus());
        }
        c.setStatus(ContractStatus.ACTIVE);
        auditService.log(null, null, "ACTIVATE_CONTRACT", "CONTRACT", id,
                "contractNo=" + c.getContractNo(), null);
        Contract saved = contractRepository.save(c);
        notifyContractLifecycle(saved, "合同已生效", "合同 " + saved.getContractNo() + "（" + saved.getTitle() + "）已激活。");
        return saved;
    }

    @Transactional
    public Contract terminate(Long id) {
        Contract c = getDetail(id);
        if (c.getStatus() != ContractStatus.ACTIVE) {
            throw new BadRequestException("仅生效合同可终止，当前状态: " + c.getStatus());
        }
        c.setStatus(ContractStatus.TERMINATED);
        auditService.log(null, null, "TERMINATE_CONTRACT", "CONTRACT", id,
                "contractNo=" + c.getContractNo(), null);
        Contract saved = contractRepository.save(c);
        notifyContractLifecycle(saved, "合同已终止", "合同 " + saved.getContractNo() + "（" + saved.getTitle() + "）已终止。");
        return saved;
    }

    private void notifyContractLifecycle(Contract c, String title, String content) {
        try {
            notificationService.send(
                    null,
                    c.getSupplier().getId(),
                    title,
                    content,
                    "CONTRACT_LIFECYCLE",
                    "CONTRACT",
                    c.getId());
        } catch (Exception e) {
            log.warn("合同生命周期通知（供应商）失败: {}", e.getMessage());
        }
        try {
            staffNotificationService.notifyProcurementOrgStakeholders(
                    c.getProcurementOrg().getId(),
                    title,
                    content,
                    "CONTRACT_LIFECYCLE",
                    "CONTRACT",
                    c.getId());
        } catch (Exception e) {
            log.warn("合同生命周期通知（内部）失败: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Contract> listExpiring(Long orgId, int daysAhead) {
        LocalDate deadline = LocalDate.now().plusDays(daysAhead);
        List<Contract> expiring = contractRepository.findByStatusAndEndDateBefore(ContractStatus.ACTIVE, deadline);
        if (orgId != null) {
            return expiring.stream()
                    .filter(c -> c.getProcurementOrg().getId().equals(orgId))
                    .toList();
        }
        return expiring;
    }

    private String generateContractNo() {
        String prefix = "CTR" + Year.now().getValue() + "-";
        long count = contractRepository.count() + 1;
        return prefix + String.format("%05d", count);
    }

    public record CreateContractLine(
            Long materialId,
            String materialDesc,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice,
            String remark
    ) {}
}
