package com.srm.master.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.domain.SupplierLifecycleStatus;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.SupplierRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final SupplierRepository supplierRepository;
    private final MaterialItemRepository materialItemRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final MaterialDerivedMasterService materialDerivedMasterService;

    /**
     * 下单/下拉用：仅包含物料中出现的 U9 供应商；缺失时在 supplier 表按编码自动建档并授权全部采购组织。
     */
    @Transactional
    public List<Supplier> listSuppliers() {
        List<MaterialDerivedMasterService.MaterialSupplierRefRow> refs =
                materialDerivedMasterService.listAllMaterialSupplierRefs();
        if (refs.isEmpty()) {
            return List.of();
        }
        List<OrgUnit> procOrgs = orgUnitRepository.findByOrgType(OrgUnitType.PROCUREMENT);
        Set<Long> allProcIds = procOrgs.stream().map(OrgUnit::getId).collect(Collectors.toCollection(HashSet::new));
        if (allProcIds.isEmpty()) {
            return List.of();
        }
        for (MaterialDerivedMasterService.MaterialSupplierRefRow ref : refs) {
            if (ref == null || !StringUtils.hasText(ref.u9SupplierCode())) {
                continue;
            }
            upsertSupplierMasterForU9(ref.u9SupplierCode().trim(), ref.u9SupplierName(), allProcIds);
        }
        Set<String> allowed = refs.stream()
                .filter(r -> r != null && StringUtils.hasText(r.u9SupplierCode()))
                .map(r -> r.u9SupplierCode().trim())
                .collect(Collectors.toSet());
        List<Supplier> out = new ArrayList<>();
        for (String code : allowed.stream().sorted().toList()) {
            supplierRepository.findByCode(code).ifPresent(out::add);
        }
        return out;
    }

    /**
     * lpgys 等写入物料侧供应商后同步调用，使「主数据-供应商」与 {@link #listSuppliers()} 使用的 supplier 主档立即一致。
     *
     * @param u9SupplierCode 已规范化的 U9 供应商编码（非空）
     * @param u9SupplierName 可为 null，则展示名回退为编码
     */
    @Transactional
    public void upsertSupplierMasterForU9(String u9SupplierCode, String u9SupplierName) {
        if (!StringUtils.hasText(u9SupplierCode)) {
            return;
        }
        List<OrgUnit> procOrgs = orgUnitRepository.findByOrgType(OrgUnitType.PROCUREMENT);
        Set<Long> allProcIds = procOrgs.stream().map(OrgUnit::getId).collect(Collectors.toCollection(HashSet::new));
        if (allProcIds.isEmpty()) {
            return;
        }
        upsertSupplierMasterForU9(u9SupplierCode.trim(), u9SupplierName, allProcIds);
    }

    private void upsertSupplierMasterForU9(String code, String u9SupplierName, Set<Long> allProcurementOrgIds) {
        String name = StringUtils.hasText(u9SupplierName) ? u9SupplierName.trim() : code;
        Optional<Supplier> existing = supplierRepository.findByCode(code);
        if (existing.isPresent()) {
            Supplier s = existing.get();
            if (!name.equals(s.getName())) {
                s.setName(name);
            }
            if (!StringUtils.hasText(s.getU9VendorCode())) {
                s.setU9VendorCode(code);
            }
            s.getAuthorizedProcurementOrgs().clear();
            attachOrgScopes(s, allProcurementOrgIds);
            supplierRepository.save(s);
        } else {
            Supplier s = new Supplier();
            s.setCode(code);
            s.setName(name);
            s.setU9VendorCode(code);
            attachOrgScopes(s, allProcurementOrgIds);
            supplierRepository.save(s);
        }
    }

    @Transactional(readOnly = true)
    public Supplier requireSupplier(Long id) {
        return supplierRepository.fetchWithOrgs(id)
                .orElseThrow(() -> new NotFoundException("供应商不存在: " + id));
    }

    @Transactional
    public Supplier createSupplier(String code, String name, String u9VendorCode, String taxId,
                                   Set<Long> procurementOrgIds) {
        if (supplierRepository.existsByCode(code)) {
            throw new BadRequestException("供应商编码已存在: " + code);
        }
        Supplier s = new Supplier();
        s.setCode(code);
        s.setName(name);
        s.setU9VendorCode(u9VendorCode);
        s.setTaxId(taxId);
        attachOrgScopes(s, procurementOrgIds);
        return supplierRepository.save(s);
    }

    @Transactional
    public Supplier updateSupplier(Long id, String name, String u9VendorCode, String taxId,
                                   Set<Long> procurementOrgIds) {
        Supplier s = requireSupplier(id);
        s.setName(name);
        s.setU9VendorCode(u9VendorCode);
        s.setTaxId(taxId);
        s.getAuthorizedProcurementOrgs().clear();
        attachOrgScopes(s, procurementOrgIds);
        return supplierRepository.save(s);
    }

    private void attachOrgScopes(Supplier s, Set<Long> procurementOrgIds) {
        if (procurementOrgIds == null || procurementOrgIds.isEmpty()) {
            return;
        }
        for (Long oid : procurementOrgIds) {
            OrgUnit ou = orgUnitRepository.findById(oid)
                    .orElseThrow(() -> new NotFoundException("组织不存在: " + oid));
            if (ou.getOrgType() != OrgUnitType.PROCUREMENT) {
                throw new BadRequestException("供应商授权仅可选择采购组织，id=" + oid);
            }
            s.getAuthorizedProcurementOrgs().add(ou);
        }
    }

    @Transactional(readOnly = true)
    public List<MaterialItem> listMaterials() {
        return materialItemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<MaterialItem> pageMaterials(Pageable pageable) {
        return materialItemRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public MaterialItem requireMaterial(Long id) {
        return materialItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("物料不存在: " + id));
    }

    @Transactional
    public MaterialItem createMaterial(String code, String name, String uom, String u9ItemCode) {
        if (materialItemRepository.existsByCode(code)) {
            throw new BadRequestException("物料编码已存在: " + code);
        }
        MaterialItem m = new MaterialItem();
        m.setCode(code);
        m.setName(name);
        m.setUom(uom);
        m.setU9ItemCode(u9ItemCode);
        return materialItemRepository.save(m);
    }

    @Transactional
    public MaterialItem updateMaterial(Long id, String name, String uom, String u9ItemCode) {
        MaterialItem m = requireMaterial(id);
        m.setName(name);
        m.setUom(uom);
        m.setU9ItemCode(u9ItemCode);
        return materialItemRepository.save(m);
    }

    /** 校验供应商是否可在该采购组织交易 */
    public void assertSupplierAuthorizedForOrg(Supplier supplier, OrgUnit procurementOrg) {
        boolean ok = supplier.getAuthorizedProcurementOrgs().stream()
                .map(OrgUnit::getId)
                .collect(Collectors.toSet())
                .contains(procurementOrg.getId());
        if (!ok) {
            throw new BadRequestException("供应商未授权在当前采购组织交易: " + procurementOrg.getCode());
        }
    }

    /**
     * 新建 PO / PR 转单等场景：禁止待审核、黑名单、淘汰供应商参与采购。
     */
    public void assertSupplierAllowedForPurchaseOrder(Supplier supplier) {
        SupplierLifecycleStatus st = supplier.getLifecycleStatus();
        if (st == SupplierLifecycleStatus.PENDING_REVIEW
                || st == SupplierLifecycleStatus.BLACKLISTED
                || st == SupplierLifecycleStatus.ELIMINATED) {
            throw new BadRequestException(
                    "供应商状态为 " + st.name() + "，不可新建采购订单或请购转单: " + supplier.getCode());
        }
    }
}
