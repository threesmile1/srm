package com.srm.invoice.service;

import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.GoodsReceiptLine;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.service.AuditService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.invoice.domain.*;
import com.srm.invoice.repo.InvoiceRepository;
import com.srm.invoice.repo.ReconciliationRepository;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
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
public class InvoiceService {

    /** 关联订单行时，发票单价与 PO 行单价允许偏差（含税/未税口径以 PO 为准） */
    private static final BigDecimal UNIT_PRICE_TOLERANCE = new BigDecimal("0.0100");

    private final InvoiceRepository invoiceRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final MasterDataService masterDataService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    private static long invoiceSeq = System.currentTimeMillis() % 100000;

    private synchronized String nextInvoiceNo() {
        invoiceSeq++;
        return "INV" + Year.now().getValue() + "-" + String.format("%06d", invoiceSeq);
    }

    // --- Invoice CRUD ---

    @Transactional(readOnly = true)
    public List<Invoice> listByOrg(Long orgId) {
        return invoiceRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> listBySupplier(Long supplierId) {
        return invoiceRepository.findBySupplierIdOrderByIdDesc(supplierId);
    }

    @Transactional(readOnly = true)
    public Invoice requireDetail(Long id) {
        return invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("发票不存在: " + id));
    }

    @Transactional
    public Invoice createInvoice(Long supplierId, Long procurementOrgId, LocalDate invoiceDate,
                                  String currency, BigDecimal taxAmount, String remark,
                                  List<InvoiceLineInput> lineInputs) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在"));

        Invoice inv = new Invoice();
        inv.setInvoiceNo(nextInvoiceNo());
        inv.setSupplier(supplier);
        inv.setProcurementOrg(org);
        inv.setInvoiceDate(invoiceDate);
        inv.setCurrency(currency != null ? currency : "CNY");
        inv.setTaxAmount(taxAmount != null ? taxAmount : BigDecimal.ZERO);
        inv.setStatus(InvoiceStatus.SUBMITTED);
        inv.setRemark(remark);

        BigDecimal total = BigDecimal.ZERO;
        int n = 1;
        for (InvoiceLineInput li : lineInputs) {
            validateInvoiceLineAgainstDocs(supplierId, procurementOrgId, li, n);

            InvoiceLine line = new InvoiceLine();
            line.setInvoice(inv);
            line.setLineNo(n++);
            line.setMaterialCode(li.materialCode());
            line.setMaterialName(li.materialName());
            line.setQty(li.qty());
            line.setUnitPrice(li.unitPrice());
            BigDecimal amount = li.qty().multiply(li.unitPrice()).setScale(4, RoundingMode.HALF_UP);
            line.setAmount(amount);
            line.setTaxRate(li.taxRate());

            if (li.purchaseOrderId() != null) {
                PurchaseOrder po = purchaseOrderRepository.findById(li.purchaseOrderId()).orElse(null);
                line.setPurchaseOrder(po);
            }
            if (li.purchaseOrderLineId() != null) {
                PurchaseOrderLine pol = purchaseOrderLineRepository.findById(li.purchaseOrderLineId()).orElse(null);
                line.setPurchaseOrderLine(pol);
            }
            if (li.goodsReceiptId() != null) {
                GoodsReceipt gr = goodsReceiptRepository.findById(li.goodsReceiptId()).orElse(null);
                line.setGoodsReceipt(gr);
            }
            inv.getLines().add(line);
            total = total.add(amount);
        }

        inv.setTotalAmount(total);
        Invoice saved = invoiceRepository.save(inv);
        auditService.log(null, null, "CREATE_INVOICE", "INVOICE", saved.getId(),
                "invoiceNo=" + saved.getInvoiceNo() + " amount=" + total, null);
        staffNotificationService.notifyProcurementOrgStakeholders(
                procurementOrgId,
                "供应商提交发票",
                "发票 " + saved.getInvoiceNo() + " 已由供应商 "
                        + supplier.getName() + " 提交，合计金额 " + total + "。",
                "INVOICE_SUBMITTED",
                "INVOICE",
                saved.getId());
        return saved;
    }

    @Transactional
    public Invoice confirmInvoice(Long id) {
        Invoice inv = requireDetail(id);
        if (inv.getStatus() != InvoiceStatus.SUBMITTED) {
            throw new BadRequestException("仅已提交的发票可确认");
        }
        inv.setStatus(InvoiceStatus.CONFIRMED);
        Invoice saved = invoiceRepository.save(inv);
        auditService.log(null, null, "CONFIRM_INVOICE", "INVOICE", id,
                "invoiceNo=" + inv.getInvoiceNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "发票已确认",
                    "发票号 " + saved.getInvoiceNo() + " 已由采购方确认。",
                    "INVOICE_CONFIRMED",
                    "INVOICE",
                    saved.getId());
        } catch (Exception e) {
            log.warn("发票确认后写入供应商通知失败: {}", e.getMessage());
        }
        return saved;
    }

    @Transactional
    public Invoice rejectInvoice(Long id, String reason) {
        Invoice inv = requireDetail(id);
        if (inv.getStatus() != InvoiceStatus.SUBMITTED) {
            throw new BadRequestException("仅已提交的发票可退回");
        }
        inv.setStatus(InvoiceStatus.REJECTED);
        Invoice saved = invoiceRepository.save(inv);
        auditService.log(null, null, "REJECT_INVOICE", "INVOICE", id,
                "invoiceNo=" + inv.getInvoiceNo() + " reason=" + reason, null);
        String tail = reason != null && !reason.isBlank() ? (" 原因：" + reason) : "";
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "发票已退回",
                    "发票号 " + saved.getInvoiceNo() + " 已被采购方退回。" + tail,
                    "INVOICE_REJECTED",
                    "INVOICE",
                    saved.getId());
        } catch (Exception e) {
            log.warn("发票退回后写入供应商通知失败: {}", e.getMessage());
        }
        return saved;
    }

    // --- Reconciliation ---

    @Transactional(readOnly = true)
    public List<Reconciliation> listReconByOrg(Long orgId) {
        return reconciliationRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<Reconciliation> listReconBySupplier(Long supplierId) {
        return reconciliationRepository.findBySupplierIdOrderByIdDesc(supplierId);
    }

    @Transactional
    public Reconciliation createReconciliation(Long supplierId, Long procurementOrgId,
                                                LocalDate periodFrom, LocalDate periodTo,
                                                String remark) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在"));

        BigDecimal poAmt = purchaseOrderRepository.sumAmountBySupplierAndOrgAndPeriod(
                supplierId, procurementOrgId, periodFrom, periodTo);
        BigDecimal grAmt = goodsReceiptRepository.sumAmountBySupplierAndOrgAndPeriod(
                supplierId, procurementOrgId, periodFrom, periodTo);
        BigDecimal invAmt = invoiceRepository.sumAmountBySupplierAndOrgAndPeriod(
                supplierId, procurementOrgId, periodFrom, periodTo);

        if (poAmt == null) poAmt = BigDecimal.ZERO;
        if (grAmt == null) grAmt = BigDecimal.ZERO;
        if (invAmt == null) invAmt = BigDecimal.ZERO;

        Reconciliation recon = new Reconciliation();
        recon.setReconNo("REC" + Year.now().getValue() + "-" + String.format("%05d", System.currentTimeMillis() % 100000));
        recon.setSupplier(supplier);
        recon.setProcurementOrg(org);
        recon.setPeriodFrom(periodFrom);
        recon.setPeriodTo(periodTo);
        recon.setPoAmount(poAmt);
        recon.setGrAmount(grAmt);
        recon.setInvoiceAmount(invAmt);
        recon.setDiffAmount(poAmt.subtract(invAmt));
        recon.setStatus(ReconStatus.DRAFT);
        recon.setRemark(remark);

        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "CREATE_RECON", "RECONCILIATION", saved.getId(),
                "reconNo=" + saved.getReconNo(), null);
        return saved;
    }

    @Transactional
    public Reconciliation confirmRecon(Long id) {
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        recon.setStatus(ReconStatus.CONFIRMED);
        return reconciliationRepository.save(recon);
    }

    /**
     * T12 最小硬规则：关联 {@link PurchaseOrderLine} 时校验供应商/组织、累计数量≤已收、单价与订单行一致；
     * 同时关联 {@link GoodsReceipt} 时校验头及收货单行数量。
     */
    private void validateInvoiceLineAgainstDocs(Long supplierId, Long procurementOrgId,
                                                InvoiceLineInput li, int displayLineNo) {
        String prefix = "第" + displayLineNo + "行：";

        if (li.purchaseOrderLineId() != null) {
            PurchaseOrderLine pol = purchaseOrderLineRepository.findWithPoById(li.purchaseOrderLineId())
                    .orElseThrow(() -> new BadRequestException(prefix + "采购订单行不存在"));
            PurchaseOrder po = pol.getPurchaseOrder();
            if (!po.getSupplier().getId().equals(supplierId)) {
                throw new BadRequestException(prefix + "订单行不属于当前供应商");
            }
            if (!po.getProcurementOrg().getId().equals(procurementOrgId)) {
                throw new BadRequestException(prefix + "订单行不属于当前采购组织");
            }
            if (li.purchaseOrderId() != null && !li.purchaseOrderId().equals(po.getId())) {
                throw new BadRequestException(prefix + "采购订单头与订单行不一致");
            }

            BigDecimal received = pol.getReceivedQty() != null ? pol.getReceivedQty() : BigDecimal.ZERO;
            BigDecimal priorQty = invoiceRepository.sumInvoicedQtyByPurchaseOrderLineId(pol.getId());
            if (priorQty == null) {
                priorQty = BigDecimal.ZERO;
            }
            BigDecimal after = priorQty.add(li.qty());
            if (after.compareTo(received) > 0) {
                throw new BadRequestException(prefix + "累计开票数量不能超过订单行已收数量（已收 "
                        + received.stripTrailingZeros().toPlainString() + "，已开票 "
                        + priorQty.stripTrailingZeros().toPlainString() + "）");
            }

            if (pol.getUnitPrice() != null && li.unitPrice() != null) {
                BigDecimal diff = pol.getUnitPrice().subtract(li.unitPrice()).abs();
                if (diff.compareTo(UNIT_PRICE_TOLERANCE) > 0) {
                    throw new BadRequestException(prefix + "单价与订单行不一致（订单行单价 "
                            + pol.getUnitPrice().stripTrailingZeros().toPlainString() + "）");
                }
            }

            if (li.materialCode() != null && !li.materialCode().isBlank()
                    && pol.getMaterial() != null
                    && !li.materialCode().trim().equals(pol.getMaterial().getCode())) {
                throw new BadRequestException(prefix + "物料编码与订单行物料不一致");
            }
        } else if (li.purchaseOrderId() != null) {
            PurchaseOrder po = purchaseOrderRepository.findById(li.purchaseOrderId())
                    .orElseThrow(() -> new BadRequestException(prefix + "采购订单不存在"));
            if (!po.getSupplier().getId().equals(supplierId)) {
                throw new BadRequestException(prefix + "订单不属于当前供应商");
            }
            if (!po.getProcurementOrg().getId().equals(procurementOrgId)) {
                throw new BadRequestException(prefix + "订单不属于当前采购组织");
            }
        }

        if (li.goodsReceiptId() != null) {
            GoodsReceipt gr = goodsReceiptRepository.findWithDetailsById(li.goodsReceiptId())
                    .orElseThrow(() -> new BadRequestException(prefix + "收货单不存在"));
            if (!gr.getSupplier().getId().equals(supplierId)) {
                throw new BadRequestException(prefix + "收货单不属于当前供应商");
            }
            if (!gr.getProcurementOrg().getId().equals(procurementOrgId)) {
                throw new BadRequestException(prefix + "收货单不属于当前采购组织");
            }
            if (li.purchaseOrderLineId() != null) {
                Long polId = li.purchaseOrderLineId();
                GoodsReceiptLine grl = gr.getLines().stream()
                        .filter(l -> l.getPurchaseOrderLine().getId().equals(polId))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException(prefix + "收货单不含该采购订单行"));
                if (li.qty().compareTo(grl.getReceivedQty()) > 0) {
                    throw new BadRequestException(prefix + "开票数量不能超过收货单行数量 "
                            + grl.getReceivedQty().stripTrailingZeros().toPlainString());
                }
            }
        }
    }

    public record InvoiceLineInput(
            String materialCode, String materialName,
            BigDecimal qty, BigDecimal unitPrice, BigDecimal taxRate,
            Long purchaseOrderId, Long purchaseOrderLineId, Long goodsReceiptId
    ) {}
}
