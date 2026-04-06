package com.srm.po.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.domain.Warehouse;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.repo.WarehouseRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.repo.MaterialItemRepository;
import com.srm.master.repo.SupplierRepository;
import com.srm.master.service.MasterDataService;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.service.PurchaseOrderService.CreateLine;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PoImportService {

    private final PurchaseOrderService purchaseOrderService;
    private final OrgUnitRepository orgUnitRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialItemRepository materialItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final MasterDataService masterDataService;

    public record PoImportResult(int totalRows, int ordersCreated, int linesCreated, List<String> errors) {}

    /**
     * PO Excel 导入。
     * 列顺序: 采购组织编码 | 供应商编码 | 币种 | 备注 | 物料编码 | 仓库编码 | 数量 | 单价 | 交期(yyyy-MM-dd)
     * 相同(采购组织+供应商+币种+备注)的连续行归入同一个PO。
     */
    @Transactional
    public PoImportResult importOrders(MultipartFile file) {
        List<String> errors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add("文件为空");
            return new PoImportResult(0, 0, 0, errors);
        }

        List<ParsedRow> parsedRows;
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            parsedRows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String orgCode = cellStr(row, 0);
                if (orgCode.isBlank()) continue;
                parsedRows.add(new ParsedRow(
                        i + 1,
                        orgCode,
                        cellStr(row, 1),
                        cellStr(row, 2),
                        cellStr(row, 3),
                        cellStr(row, 4),
                        cellStr(row, 5),
                        cellStr(row, 6),
                        cellStr(row, 7),
                        cellDate(row, 8)
                ));
            }
        } catch (Exception e) {
            errors.add("Excel 解析失败: " + e.getMessage());
            return new PoImportResult(0, 0, 0, errors);
        }

        if (parsedRows.isEmpty()) {
            errors.add("未找到有效数据行");
            return new PoImportResult(0, 0, 0, errors);
        }

        Map<String, List<ParsedRow>> groups = new LinkedHashMap<>();
        for (ParsedRow pr : parsedRows) {
            String key = pr.orgCode + "|" + pr.supplierCode + "|" + pr.currency + "|" + pr.remark;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(pr);
        }

        int ordersCreated = 0;
        int linesCreated = 0;

        for (var entry : groups.entrySet()) {
            List<ParsedRow> groupRows = entry.getValue();
            ParsedRow first = groupRows.get(0);
            String rowRange = first.rowNum + "-" + groupRows.get(groupRows.size() - 1).rowNum;

            Optional<OrgUnit> orgOpt = orgUnitRepository.findByCode(first.orgCode);
            if (orgOpt.isEmpty() || orgOpt.get().getOrgType() != OrgUnitType.PROCUREMENT) {
                errors.add("行 " + rowRange + "：采购组织编码无效 '" + first.orgCode + "'");
                continue;
            }
            OrgUnit org = orgOpt.get();

            Optional<Supplier> supOpt = supplierRepository.findByCode(first.supplierCode);
            if (supOpt.isEmpty()) {
                errors.add("行 " + rowRange + "：供应商编码无效 '" + first.supplierCode + "'");
                continue;
            }
            Supplier supplier = supOpt.get();

            List<CreateLine> createLines = new ArrayList<>();
            boolean groupHasError = false;

            for (ParsedRow pr : groupRows) {
                Optional<MaterialItem> matOpt = materialItemRepository.findByCode(pr.materialCode);
                if (matOpt.isEmpty()) {
                    errors.add("第 " + pr.rowNum + " 行：物料编码无效 '" + pr.materialCode + "'");
                    groupHasError = true;
                    continue;
                }
                Optional<Warehouse> whOpt = warehouseRepository.findByProcurementOrgAndCode(org, pr.warehouseCode);
                if (whOpt.isEmpty()) {
                    errors.add("第 " + pr.rowNum + " 行：仓库编码无效（在组织 " + org.getCode() + " 下）'" + pr.warehouseCode + "'");
                    groupHasError = true;
                    continue;
                }
                BigDecimal qty;
                try {
                    qty = new BigDecimal(pr.qty);
                    if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    errors.add("第 " + pr.rowNum + " 行：数量无效 '" + pr.qty + "'");
                    groupHasError = true;
                    continue;
                }
                BigDecimal price;
                try {
                    price = new BigDecimal(pr.unitPrice);
                    if (price.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    errors.add("第 " + pr.rowNum + " 行：单价无效 '" + pr.unitPrice + "'");
                    groupHasError = true;
                    continue;
                }

                createLines.add(new CreateLine(
                        matOpt.get().getId(),
                        whOpt.get().getId(),
                        qty,
                        null,
                        price,
                        pr.requestedDate
                ));
            }

            if (groupHasError || createLines.isEmpty()) continue;

            try {
                String currency = first.currency.isBlank() ? "CNY" : first.currency;
                String remark = first.remark.isBlank() ? null : first.remark;
                purchaseOrderService.create(org.getId(), supplier.getId(), currency, remark, createLines);
                ordersCreated++;
                linesCreated += createLines.size();
            } catch (Exception e) {
                errors.add("行 " + rowRange + "：创建PO失败 - " + e.getMessage());
            }
        }

        return new PoImportResult(parsedRows.size(), ordersCreated, linesCreated, errors);
    }

    private record ParsedRow(int rowNum, String orgCode, String supplierCode, String currency,
                              String remark, String materialCode, String warehouseCode,
                              String qty, String unitPrice, LocalDate requestedDate) {}

    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        return cell.getStringCellValue().trim();
    }

    private LocalDate cellDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String s = cell.getStringCellValue().trim();
            if (s.isBlank()) return null;
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
