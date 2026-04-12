package com.srm.invoice.service;

import com.srm.execution.domain.GoodsReceipt;
import com.srm.execution.domain.GoodsReceiptLine;
import com.srm.execution.repo.GoodsReceiptRepository;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.foundation.service.AuditService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.invoice.domain.Invoice;
import com.srm.invoice.domain.InvoiceAttachment;
import com.srm.invoice.domain.InvoiceKind;
import com.srm.invoice.domain.InvoiceLine;
import com.srm.invoice.domain.InvoiceStatus;
import com.srm.invoice.domain.ReconStatus;
import com.srm.invoice.domain.Reconciliation;
import com.srm.invoice.repo.InvoiceAttachmentRepository;
import com.srm.invoice.repo.InvoiceRepository;
import com.srm.invoice.repo.ReconciliationRepository;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderLineRepository;
import com.srm.po.repo.PurchaseOrderRepository;
import com.srm.invoice.web.InvoiceController;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.ForbiddenException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final long MAX_INVOICE_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    /** 关联订单行时，发票单价与 PO 行单价允许偏差（含税/未税口径以 PO 为准） */
    private static final BigDecimal UNIT_PRICE_TOLERANCE = new BigDecimal("0.0100");

    /** 对账「收货 vs 票」差异在容差内视为平（元） */
    private static final BigDecimal RECON_DIFF_TOLERANCE = new BigDecimal("0.01");

    private static long reconSeq = System.currentTimeMillis() % 100000;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceAttachmentRepository invoiceAttachmentRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final MasterDataService masterDataService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    @Value("${srm.invoice-upload-dir:${user.home}/.srm/invoice-files}")
    private String invoiceUploadRoot;

    private static long invoiceSeq = System.currentTimeMillis() % 100000;

    private synchronized String nextInvoiceNo() {
        invoiceSeq++;
        return "INV" + Year.now().getValue() + "-" + String.format("%06d", invoiceSeq);
    }

    private synchronized String nextReconNo() {
        reconSeq++;
        return "REC" + Year.now().getValue() + "-" + String.format("%06d", reconSeq);
    }

    /**
     * 明细税率（0–100）× 行金额 → 税额；无税率时返回 0。
     * 与门户「税率(%)」一致，作为头表税额主数据源（甄云类：以明细价税汇总为准）。
     */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 税务发票代码/号码校验：专票必填；普票可空，若填则代码与号码成对出现。
     */
    private void validateVatInvoiceMetadata(InvoiceKind kind, String code, String number) {
        if (kind == InvoiceKind.SPECIAL_VAT) {
            if (code == null || number == null) {
                throw new BadRequestException("增值税专用发票须同时填写税务发票代码与号码");
            }
        } else {
            if ((code == null) != (number == null)) {
                throw new BadRequestException("税务发票代码与号码须同时填写或同时留空");
            }
        }
        if (code != null && !code.matches("\\d{10,12}")) {
            throw new BadRequestException("税务发票代码须为10–12位数字");
        }
        if (number != null && !number.matches("\\d{8,20}")) {
            throw new BadRequestException("税务发票号码须为8–20位数字");
        }
    }

    private BigDecimal deriveTaxFromLines(List<InvoiceLineInput> lineInputs) {
        BigDecimal tax = BigDecimal.ZERO;
        for (InvoiceLineInput li : lineInputs) {
            BigDecimal lineAmt = li.qty().multiply(li.unitPrice()).setScale(4, RoundingMode.HALF_UP);
            if (li.taxRate() != null && li.taxRate().compareTo(BigDecimal.ZERO) > 0) {
                tax = tax.add(lineAmt.multiply(li.taxRate())
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            }
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
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

    private Path invoiceStorageRoot() {
        return Paths.get(invoiceUploadRoot).toAbsolutePath().normalize();
    }

    private static String sanitizeAttachmentOriginalName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "file";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            return "file";
        }
        return name.length() > 400 ? name.substring(0, 400) : name;
    }

    private static String fileExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /**
     * 门户上传发票扫描件/PDF（提交发票成功后调用）。
     */
    @Transactional
    public InvoiceAttachmentBrief addPortalInvoiceAttachment(Long invoiceId, Long supplierId, MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择文件");
        }
        if (file.getSize() > MAX_INVOICE_ATTACHMENT_BYTES) {
            throw new BadRequestException("附件不能超过 10MB");
        }
        Invoice inv = requireDetail(invoiceId);
        if (!inv.getSupplier().getId().equals(supplierId)) {
            throw new ForbiddenException("无权上传附件");
        }
        String original = sanitizeAttachmentOriginalName(file.getOriginalFilename());
        Path root = invoiceStorageRoot();
        Files.createDirectories(root);
        Path dir = root.resolve(String.valueOf(invoiceId));
        Files.createDirectories(dir);
        String ext = fileExtension(original);
        String storedFileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = dir.resolve(storedFileName);
        file.transferTo(target);
        String relative = invoiceId + "/" + storedFileName;
        InvoiceAttachment att = new InvoiceAttachment();
        att.setInvoice(inv);
        att.setOriginalName(original);
        att.setContentType(file.getContentType());
        att.setFileSize(file.getSize());
        att.setStoredPath(relative);
        invoiceAttachmentRepository.save(att);
        return new InvoiceAttachmentBrief(att.getId(), att.getOriginalName(), att.getContentType(), att.getFileSize());
    }

    @Transactional(readOnly = true)
    public InvoiceAttachmentDownload openInvoiceAttachmentDownload(long invoiceId, long attachmentId,
                                                                   Long supplierIdOrNull) {
        InvoiceAttachment att = invoiceAttachmentRepository.findByIdAndInvoice_Id(attachmentId, invoiceId)
                .orElseThrow(() -> new NotFoundException("附件不存在"));
        Invoice inv = requireDetail(invoiceId);
        if (supplierIdOrNull != null && !inv.getSupplier().getId().equals(supplierIdOrNull)) {
            throw new ForbiddenException("无权下载此附件");
        }
        Path abs = invoiceStorageRoot().resolve(att.getStoredPath()).normalize();
        Path base = invoiceStorageRoot().normalize();
        if (!abs.startsWith(base)) {
            throw new NotFoundException("附件路径非法");
        }
        if (!Files.isRegularFile(abs)) {
            throw new NotFoundException("文件已丢失");
        }
        Resource resource = new FileSystemResource(abs.toFile());
        return new InvoiceAttachmentDownload(resource, att.getOriginalName(), att.getContentType());
    }

    public record InvoiceAttachmentBrief(Long id, String originalName, String contentType, long fileSize) {}

    public record InvoiceAttachmentDownload(Resource resource, String originalFileName, String contentType) {}

    /**
     * 门户开票选行：甄云类从「可对账订单行」勾选——订单已发布或已关闭、已收货，且（已收 − 已开票）&gt; 0。
     */
    @Transactional(readOnly = true)
    public List<BillablePoLineRow> listBillablePoLinesForSupplier(Long supplierId, Long procurementOrgId) {
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findReleasedWithReceiptBySupplierAndOrg(
                supplierId, procurementOrgId);
        List<BillablePoLineRow> out = new ArrayList<>();
        for (PurchaseOrderLine l : lines) {
            BigDecimal inv = invoiceRepository.sumInvoicedQtyByPurchaseOrderLineId(l.getId());
            if (inv == null) {
                inv = BigDecimal.ZERO;
            }
            BigDecimal recv = l.getReceivedQty() != null ? l.getReceivedQty() : BigDecimal.ZERO;
            BigDecimal rem = recv.subtract(inv);
            if (rem.compareTo(BigDecimal.ZERO) < 0) {
                rem = BigDecimal.ZERO;
            }
            PurchaseOrder po = l.getPurchaseOrder();
            // 含 remaining=0 的行，便于门户区分「无数据」与「已开齐」
            out.add(new BillablePoLineRow(
                    l.getId(),
                    po.getId(),
                    po.getPoNo(),
                    l.getLineNo(),
                    l.getMaterial().getCode(),
                    l.getMaterial().getName(),
                    recv,
                    inv,
                    rem,
                    l.getUnitPrice(),
                    l.getUom()));
        }
        return out;
    }

    /** 可对账 PO 行（门户开票选行 DTO） */
    public record BillablePoLineRow(
            Long purchaseOrderLineId,
            Long purchaseOrderId,
            String poNo,
            int lineNo,
            String materialCode,
            String materialName,
            BigDecimal receivedQty,
            BigDecimal invoicedQty,
            BigDecimal remainingInvoiceableQty,
            BigDecimal unitPrice,
            String uom
    ) {}

    /**
     * 不可标 readOnly：会被 {@link #confirmInvoice}、{@link #rejectInvoice} 等写流程调用，
     * 嵌套事务若带 readOnly=true 可能把外层写事务标成只读，导致确认/退回无法落库。
     */
    @Transactional
    public Invoice requireDetail(Long id) {
        return invoiceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("发票不存在: " + id));
    }

    @Transactional
    public InvoiceController.InvoiceDetail createInvoice(Long supplierId, Long procurementOrgId, LocalDate invoiceDate,
                                  String currency, BigDecimal taxAmount, String remark,
                                  InvoiceKind invoiceKind, String vatInvoiceCode, String vatInvoiceNumber,
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
        inv.setStatus(InvoiceStatus.SUBMITTED);
        inv.setRemark(remark);
        InvoiceKind k = invoiceKind != null ? invoiceKind : InvoiceKind.ORDINARY_VAT;
        inv.setInvoiceKind(k);
        inv.setVatInvoiceCode(trimToNull(vatInvoiceCode));
        inv.setVatInvoiceNumber(trimToNull(vatInvoiceNumber));
        validateVatInvoiceMetadata(k, inv.getVatInvoiceCode(), inv.getVatInvoiceNumber());

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
        BigDecimal derivedTax = deriveTaxFromLines(lineInputs);
        if (derivedTax.compareTo(BigDecimal.ZERO) > 0) {
            inv.setTaxAmount(derivedTax);
        } else {
            inv.setTaxAmount(taxAmount != null ? taxAmount : BigDecimal.ZERO);
        }
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
        return InvoiceController.InvoiceDetail.from(requireDetail(saved.getId()));
    }

    /** OSIV=false 时须在 Service 事务内组装 DTO，避免懒加载；勿依赖 Controller 上 @Transactional */
    @Transactional(readOnly = true)
    public InvoiceController.InvoiceDetail getInvoiceDetailDto(Long id) {
        return InvoiceController.InvoiceDetail.from(requireDetail(id));
    }

    @Transactional(readOnly = true)
    public InvoiceController.InvoiceDetail getPortalInvoiceDetailDto(Long id, long supplierId) {
        Invoice inv = requireDetail(id);
        if (!inv.getSupplier().getId().equals(supplierId)) {
            throw new ForbiddenException("无权查看此发票");
        }
        return InvoiceController.InvoiceDetail.from(inv);
    }

    @Transactional
    public InvoiceController.InvoiceDetail confirmInvoice(Long id) {
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
        return InvoiceController.InvoiceDetail.from(requireDetail(id));
    }

    @Transactional
    public InvoiceController.InvoiceDetail rejectInvoice(Long id, String reason) {
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
        return InvoiceController.InvoiceDetail.from(requireDetail(id));
    }

    // --- Reconciliation ---

    @Transactional(readOnly = true)
    public List<Reconciliation> listReconByOrg(Long orgId) {
        return reconciliationRepository.findWithDetailsByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<Reconciliation> listReconBySupplier(Long supplierId) {
        return reconciliationRepository.findWithDetailsBySupplierIdOrderByIdDesc(supplierId);
    }

    /**
     * 事务内在返回给 API 层组装 {@link InvoiceController.ReconSummary} 前补全 Supplier 标量属性。
     * open-in-view=false 时，仅调用 {@link Supplier#getId()} 不会初始化懒代理，会话外 {@code getCode()}/{@code getName()} 会失败。
     */
    private static void touchSupplierForSummary(Reconciliation r) {
        Supplier s = r.getSupplier();
        if (s != null) {
            s.getCode();
            s.getName();
        }
    }

    /**
     * 对账汇总口径：<strong>仅按收货月</strong>（收货单 {@code receipt_date} 落在期间内）。
     * PO 金额列与收货金额同为「按收货行×订单行单价」汇总，二者一致；发票金额为已确认发票中、
     * 关联收货单且该收货单收货日期在期间内的发票<strong>行</strong>金额合计。
     *
     * @param supplierInitiated true：甄云类「月末供应商在 SRM 发起对账」→ 生成后直接进入待采购确认；
     *                          false：采购侧生成对账单 → 待供应商确认
     */
    @Transactional
    public Reconciliation createReconciliation(Long supplierId, Long procurementOrgId,
                                                LocalDate periodFrom, LocalDate periodTo,
                                                String remark, boolean supplierInitiated) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        OrgUnit org = orgUnitRepository.findById(procurementOrgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在"));

        if (periodFrom == null || periodTo == null) {
            throw new BadRequestException("请指定对账期间起止");
        }
        if (periodFrom.isAfter(periodTo)) {
            throw new BadRequestException("对账期间起不能晚于结束日期");
        }

        BigDecimal grAmt = goodsReceiptRepository.sumAmountBySupplierAndOrgAndPeriod(
                supplierId, procurementOrgId, periodFrom, periodTo);
        // 收货月口径下，订单侧展示金额与收货计价一致（均为入库执行额）
        BigDecimal poAmt = grAmt;
        BigDecimal invAmt = invoiceRepository.sumConfirmedLineAmountByGrReceiptDateInPeriod(
                supplierId, procurementOrgId, periodFrom, periodTo);

        if (poAmt == null) poAmt = BigDecimal.ZERO;
        if (grAmt == null) grAmt = BigDecimal.ZERO;
        if (invAmt == null) invAmt = BigDecimal.ZERO;

        // 核心差异：收货（应付暂估）− 已确认且关联收货落在期间内的开票
        BigDecimal diffGrVsInv = grAmt.subtract(invAmt);

        Reconciliation recon = new Reconciliation();
        recon.setReconNo(nextReconNo());
        recon.setSupplier(supplier);
        recon.setProcurementOrg(org);
        recon.setPeriodFrom(periodFrom);
        recon.setPeriodTo(periodTo);
        recon.setPoAmount(poAmt);
        recon.setGrAmount(grAmt);
        recon.setInvoiceAmount(invAmt);
        recon.setDiffAmount(diffGrVsInv);
        recon.setStatus(supplierInitiated ? ReconStatus.PENDING_PROCUREMENT : ReconStatus.PENDING_SUPPLIER);
        recon.setRemark(remark);

        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, supplierInitiated ? "CREATE_RECON_BY_SUPPLIER" : "CREATE_RECON_BY_PROCUREMENT",
                "RECONCILIATION", saved.getId(),
                "reconNo=" + saved.getReconNo(), null);
        if (supplierInitiated) {
            staffNotificationService.notifyProcurementOrgStakeholders(
                    procurementOrgId,
                    "供应商发起对账",
                    "供应商 " + supplier.getName() + " 已发起对账单 " + saved.getReconNo()
                            + "（期间 " + periodFrom + "～" + periodTo + "），请核对后确认。",
                    "RECON_SUPPLIER_INITIATED",
                    "RECONCILIATION",
                    saved.getId());
        }
        return saved;
    }

    @Transactional
    public Reconciliation confirmReconciliationBySupplier(Long reconId, Long supplierId) {
        Reconciliation recon = reconciliationRepository.findById(reconId)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (!recon.getSupplier().getId().equals(supplierId)) {
            throw new ForbiddenException("无权操作该对账单");
        }
        if (recon.getStatus() != ReconStatus.PENDING_SUPPLIER) {
            throw new BadRequestException("当前状态不可由供应商确认");
        }
        recon.setStatus(ReconStatus.PENDING_PROCUREMENT);
        recon.setSupplierConfirmedAt(Instant.now());
        recon.setProcurementRejectReason(null);
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "SUPPLIER_CONFIRM_RECON", "RECONCILIATION", reconId,
                "reconNo=" + recon.getReconNo(), null);
        staffNotificationService.notifyProcurementOrgStakeholders(
                recon.getProcurementOrg().getId(),
                "供应商已确认对账单",
                "对账单 " + recon.getReconNo() + " 已由供应商确认，请采购核对后完成确认。",
                "RECON_SUPPLIER_CONFIRMED",
                "RECONCILIATION",
                saved.getId());
        touchSupplierForSummary(saved);
        return saved;
    }

    @Transactional
    public Reconciliation confirmReconciliationByProcurement(Long id) {
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (recon.getStatus() == ReconStatus.CONFIRMED) {
            throw new BadRequestException("对账单已确认");
        }
        if (recon.getStatus() != ReconStatus.PENDING_PROCUREMENT) {
            throw new BadRequestException("须待供应商确认后，采购方可确认");
        }
        recon.setStatus(ReconStatus.CONFIRMED);
        recon.setProcurementConfirmedAt(Instant.now());
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "CONFIRM_RECON", "RECONCILIATION", id,
                "reconNo=" + recon.getReconNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "对账单已确认",
                    "对账单 " + saved.getReconNo() + " 已由采购方确认。",
                    "RECON_CONFIRMED",
                    "RECONCILIATION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("对账确认后写入供应商通知失败: {}", e.getMessage());
        }
        touchSupplierForSummary(saved);
        return saved;
    }

    private static String requireReason(String reason, String label) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException(label + "不能为空");
        }
        return reason.trim();
    }

    private void clearDisputeFields(Reconciliation recon) {
        recon.setDisputeReason(null);
        recon.setDisputedAt(null);
        recon.setDisputedBy(null);
    }

    /** 供应商：待确认状态下提出异议 → 争议 */
    @Transactional
    public Reconciliation supplierDisputeReconciliation(Long reconId, Long supplierId, String reason) {
        String r = requireReason(reason, "异议说明");
        Reconciliation recon = reconciliationRepository.findById(reconId)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (!recon.getSupplier().getId().equals(supplierId)) {
            throw new ForbiddenException("无权操作该对账单");
        }
        if (recon.getStatus() != ReconStatus.PENDING_SUPPLIER) {
            throw new BadRequestException("当前状态不可提出异议");
        }
        recon.setStatus(ReconStatus.DISPUTED);
        recon.setDisputeReason(r);
        recon.setDisputedAt(Instant.now());
        recon.setDisputedBy("SUPPLIER");
        recon.setProcurementRejectReason(null);
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "SUPPLIER_DISPUTE_RECON", "RECONCILIATION", reconId,
                "reconNo=" + recon.getReconNo(), null);
        staffNotificationService.notifyProcurementOrgStakeholders(
                recon.getProcurementOrg().getId(),
                "供应商对对账单提出异议",
                "对账单 " + recon.getReconNo() + " 异议说明：" + r,
                "RECON_DISPUTED",
                "RECONCILIATION",
                saved.getId());
        touchSupplierForSummary(saved);
        return saved;
    }

    /** 采购：待采购确认状态下驳回 → 退回供应商待确认 */
    @Transactional
    public Reconciliation procurementRejectReconciliation(Long id, String reason) {
        String r = requireReason(reason, "驳回说明");
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (recon.getStatus() != ReconStatus.PENDING_PROCUREMENT) {
            throw new BadRequestException("仅「待采购确认」状态可驳回");
        }
        recon.setStatus(ReconStatus.PENDING_SUPPLIER);
        recon.setSupplierConfirmedAt(null);
        recon.setProcurementRejectReason(r);
        clearDisputeFields(recon);
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "PROCUREMENT_REJECT_RECON", "RECONCILIATION", id,
                "reconNo=" + recon.getReconNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "对账单已被采购驳回",
                    "对账单 " + saved.getReconNo() + " 已被采购方驳回。说明：" + r,
                    "RECON_REJECTED",
                    "RECONCILIATION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("对账驳回后写入供应商通知失败: {}", e.getMessage());
        }
        touchSupplierForSummary(saved);
        return saved;
    }

    /** 采购：待采购确认状态下提出异议 → 争议 */
    @Transactional
    public Reconciliation procurementDisputeReconciliation(Long id, String reason) {
        String r = requireReason(reason, "异议说明");
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (recon.getStatus() != ReconStatus.PENDING_PROCUREMENT) {
            throw new BadRequestException("仅「待采购确认」状态可提出异议");
        }
        recon.setStatus(ReconStatus.DISPUTED);
        recon.setDisputeReason(r);
        recon.setDisputedAt(Instant.now());
        recon.setDisputedBy("PROCUREMENT");
        recon.setProcurementRejectReason(null);
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "PROCUREMENT_DISPUTE_RECON", "RECONCILIATION", id,
                "reconNo=" + recon.getReconNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "采购对对账单提出异议",
                    "对账单 " + saved.getReconNo() + " 异议说明：" + r,
                    "RECON_DISPUTED",
                    "RECONCILIATION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("对账异议后写入供应商通知失败: {}", e.getMessage());
        }
        touchSupplierForSummary(saved);
        return saved;
    }

    /** 采购：争议状态下重新打开 → 退回供应商待确认 */
    @Transactional
    public Reconciliation procurementReopenFromDispute(Long id) {
        Reconciliation recon = reconciliationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("对账单不存在"));
        if (recon.getStatus() != ReconStatus.DISPUTED) {
            throw new BadRequestException("仅「争议」状态可重新打开");
        }
        recon.setStatus(ReconStatus.PENDING_SUPPLIER);
        recon.setSupplierConfirmedAt(null);
        clearDisputeFields(recon);
        Reconciliation saved = reconciliationRepository.save(recon);
        auditService.log(null, null, "RECON_REOPEN", "RECONCILIATION", id,
                "reconNo=" + recon.getReconNo(), null);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "对账单已重新打开",
                    "对账单 " + saved.getReconNo() + " 已由采购方重新打开，请核对后再次确认。",
                    "RECON_REOPENED",
                    "RECONCILIATION",
                    saved.getId());
        } catch (Exception e) {
            log.warn("对账重新打开后写入供应商通知失败: {}", e.getMessage());
        }
        touchSupplierForSummary(saved);
        return saved;
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
