package com.srm.master.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MasterDataImportService {

    private final SupplierRepository supplierRepository;
    private final MaterialItemRepository materialItemRepository;
    private final OrgUnitRepository orgUnitRepository;

    public record ImportResult(int total, int created, int updated, int skipped, List<String> errors) {}

    /**
     * 供应商 Excel 导入。
     * 列顺序：编码 | 名称 | U9供应商编码(选填) | 税号(选填) | 授权采购组织编码(选填,逗号分隔)
     */
    @Transactional
    public ImportResult importSuppliers(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int created = 0, updated = 0, skipped = 0;
        List<Row> dataRows = parseExcel(file, errors);
        if (dataRows == null) return new ImportResult(0, 0, 0, 0, errors);

        for (Row row : dataRows) {
            int rowNum = row.getRowNum() + 1;
            String code = cellStr(row, 0);
            String name = cellStr(row, 1);
            String u9Code = cellStr(row, 2);
            String taxId = cellStr(row, 3);
            String orgCodes = cellStr(row, 4);

            if (code.isBlank()) {
                errors.add("第 " + rowNum + " 行：编码不能为空");
                skipped++;
                continue;
            }
            if (name.isBlank()) {
                errors.add("第 " + rowNum + " 行：名称不能为空");
                skipped++;
                continue;
            }

            Set<OrgUnit> orgs = new HashSet<>();
            if (!orgCodes.isBlank()) {
                for (String oc : orgCodes.split("[,，;；\\s]+")) {
                    String trimmed = oc.trim();
                    if (trimmed.isEmpty()) continue;
                    Optional<OrgUnit> ou = orgUnitRepository.findByCode(trimmed);
                    if (ou.isEmpty() || ou.get().getOrgType() != OrgUnitType.PROCUREMENT) {
                        errors.add("第 " + rowNum + " 行：采购组织编码无效 '" + trimmed + "'");
                    } else {
                        orgs.add(ou.get());
                    }
                }
            }

            try {
                Optional<Supplier> existing = supplierRepository.findByCode(code);
                if (existing.isPresent()) {
                    Supplier s = existing.get();
                    s.setName(name);
                    if (!u9Code.isBlank()) s.setU9VendorCode(u9Code);
                    if (!taxId.isBlank()) s.setTaxId(taxId);
                    if (!orgs.isEmpty()) {
                        s.getAuthorizedProcurementOrgs().clear();
                        s.getAuthorizedProcurementOrgs().addAll(orgs);
                    }
                    supplierRepository.save(s);
                    updated++;
                } else {
                    Supplier s = new Supplier();
                    s.setCode(code);
                    s.setName(name);
                    s.setU9VendorCode(u9Code.isBlank() ? null : u9Code);
                    s.setTaxId(taxId.isBlank() ? null : taxId);
                    s.setAuthorizedProcurementOrgs(orgs);
                    supplierRepository.save(s);
                    created++;
                }
            } catch (Exception e) {
                errors.add("第 " + rowNum + " 行：" + e.getMessage());
                skipped++;
            }
        }

        return new ImportResult(dataRows.size(), created, updated, skipped, errors);
    }

    /**
     * 物料 Excel 导入。
     * 列顺序：编码 | 名称 | 单位 | U9料号(选填)
     */
    @Transactional
    public ImportResult importMaterials(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int created = 0, updated = 0, skipped = 0;
        List<Row> dataRows = parseExcel(file, errors);
        if (dataRows == null) return new ImportResult(0, 0, 0, 0, errors);

        for (Row row : dataRows) {
            int rowNum = row.getRowNum() + 1;
            String code = cellStr(row, 0);
            String name = cellStr(row, 1);
            String uom = cellStr(row, 2);
            String u9Code = cellStr(row, 3);

            if (code.isBlank()) {
                errors.add("第 " + rowNum + " 行：编码不能为空");
                skipped++;
                continue;
            }
            if (name.isBlank()) {
                errors.add("第 " + rowNum + " 行：名称不能为空");
                skipped++;
                continue;
            }
            if (uom.isBlank()) {
                errors.add("第 " + rowNum + " 行：单位不能为空");
                skipped++;
                continue;
            }

            try {
                Optional<MaterialItem> existing = materialItemRepository.findByCode(code);
                if (existing.isPresent()) {
                    MaterialItem m = existing.get();
                    m.setName(name);
                    m.setUom(uom);
                    if (!u9Code.isBlank()) m.setU9ItemCode(u9Code);
                    materialItemRepository.save(m);
                    updated++;
                } else {
                    MaterialItem m = new MaterialItem();
                    m.setCode(code);
                    m.setName(name);
                    m.setUom(uom);
                    m.setU9ItemCode(u9Code.isBlank() ? null : u9Code);
                    materialItemRepository.save(m);
                    created++;
                }
            } catch (Exception e) {
                errors.add("第 " + rowNum + " 行：" + e.getMessage());
                skipped++;
            }
        }

        return new ImportResult(dataRows.size(), created, updated, skipped, errors);
    }

    private List<Row> parseExcel(MultipartFile file, List<String> errors) {
        if (file == null || file.isEmpty()) {
            errors.add("文件为空");
            return null;
        }
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            List<Row> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                if (cellStr(row, 0).isBlank() && cellStr(row, 1).isBlank()) continue;
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            errors.add("Excel 解析失败: " + e.getMessage());
            return null;
        }
    }

    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }
        return cell.getStringCellValue().trim();
    }
}
