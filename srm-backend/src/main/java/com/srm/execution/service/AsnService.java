package com.srm.execution.service;

import com.srm.execution.domain.AsnLine;
import com.srm.execution.domain.AsnNotice;
import com.srm.execution.domain.AsnStatus;
import com.srm.execution.repo.AsnLineRepository;
import com.srm.execution.repo.AsnNoticeRepository;
import com.srm.execution.repo.GoodsReceiptLineRepository;
import com.srm.notification.service.NotificationService;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsnService {

    private static final long MAX_ASN_LOGISTICS_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AsnNoticeRepository asnNoticeRepository;
    private final AsnLineRepository asnLineRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final AsnNumberAllocator asnNumberAllocator;
    private final MasterDataService masterDataService;
    private final NotificationService notificationService;

    @Value("${srm.asn-upload-dir:${user.home}/.srm/asn-files}")
    private String asnUploadRoot;

    @Transactional(readOnly = true)
    public List<AsnNotice> listByPurchaseOrder(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + poId));
        return asnNoticeRepository.findByPurchaseOrderOrderByIdDesc(po);
    }

    @Transactional(readOnly = true)
    public AsnNotice requireWithLines(Long asnId) {
        return asnNoticeRepository.findWithLinesById(asnId)
                .orElseThrow(() -> new NotFoundException("ASN 不存在: " + asnId));
    }

    @Transactional(readOnly = true)
    public AsnNotice requireWithLinesForSupplier(long supplierId, long asnId) {
        AsnNotice n = requireWithLines(asnId);
        if (!n.getSupplier().getId().equals(supplierId)) {
            throw new NotFoundException("ASN 不存在");
        }
        return n;
    }

    @Transactional(readOnly = true)
    public List<AsnNotice> listForSupplier(Long supplierId) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        return asnNoticeRepository.findBySupplierOrderByIdDesc(supplier);
    }

    /**
     * 门户创建 ASN（已提交）。校验：订单已发布、供应商一致、发货量不超过可发数量。
     */
    @Transactional
    public AsnNotice createFromSupplier(
            long supplierId,
            long purchaseOrderId,
            LocalDate shipDate,
            LocalDate etaDate,
            String carrier,
            String trackingNo,
            String remark,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            List<AsnLineInput> lines
    ) {
        PurchaseOrder po = purchaseOrderRepository.findWithDetailsById(purchaseOrderId)
                .orElseThrow(() -> new NotFoundException("采购订单不存在: " + purchaseOrderId));
        if (!po.getSupplier().getId().equals(supplierId)) {
            throw new ForbiddenException("无权对该订单创建发货通知");
        }
        if (po.getStatus() != PoStatus.RELEASED) {
            throw new BadRequestException("仅已发布订单可创建 ASN，当前: " + po.getStatus());
        }
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("ASN 至少一行");
        }

        Map<Long, PurchaseOrderLine> polMap = po.getLines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));

        String asnNo = asnNumberAllocator.nextAsnNo(po.getProcurementOrg());
        AsnNotice notice = new AsnNotice();
        notice.setAsnNo(asnNo);
        notice.setPurchaseOrder(po);
        notice.setSupplier(po.getSupplier());
        notice.setProcurementOrg(po.getProcurementOrg());
        notice.setShipDate(shipDate);
        notice.setEtaDate(etaDate);
        notice.setCarrier(carrier);
        notice.setTrackingNo(trackingNo);
        notice.setRemark(remark);
        notice.setReceiverName(trimToNull(receiverName));
        notice.setReceiverPhone(trimToNull(receiverPhone));
        notice.setReceiverAddress(trimToNull(receiverAddress));
        notice.setStatus(AsnStatus.SUBMITTED);

        int n = 1;
        for (AsnLineInput in : lines) {
            PurchaseOrderLine pol = polMap.get(in.purchaseOrderLineId());
            if (pol == null) {
                throw new BadRequestException("订单行不属于该订单: " + in.purchaseOrderLineId());
            }
            if (pol.getConfirmedAt() == null) {
                throw new BadRequestException(
                        "行 " + pol.getLineNo() + " 尚未回执，请在门户先确认交期与数量后再创建发货通知");
            }
            if (in.shipQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("发货数量须大于 0");
            }
            BigDecimal outstanding = pol.getQty().subtract(pol.getReceivedQty());
            BigDecimal alreadyShipped = asnLineRepository.sumShipQtySubmittedForPolLine(pol.getId());
            if (in.shipQty().add(alreadyShipped).compareTo(outstanding) > 0) {
                throw new BadRequestException(
                        "行 " + pol.getLineNo() + " 可发数量不足（订购-已收-已通知): " + outstanding.subtract(alreadyShipped));
            }
            AsnLine al = new AsnLine();
            al.setAsnNotice(notice);
            al.setPurchaseOrderLine(pol);
            al.setLineNo(n++);
            al.setShipQty(in.shipQty());
            notice.getLines().add(al);
        }

        AsnNotice saved = asnNoticeRepository.save(notice);
        try {
            notificationService.send(
                    null,
                    saved.getSupplier().getId(),
                    "发货通知已提交",
                    "ASN " + saved.getAsnNo() + " 已提交，关联订单 " + po.getPoNo() + "。",
                    "ASN_SUBMITTED",
                    "ASN",
                    saved.getId());
        } catch (Exception e) {
            log.warn("ASN 提交后写入供应商通知失败: {}", e.getMessage());
        }
        return saved;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 供应商作废发货通知：仅 {@link AsnStatus#SUBMITTED} 可作废；若已有收货单行关联该通知下的 ASN 行则不允许。
     * 作废后不再计入 {@link AsnLineRepository#sumShipQtySubmittedForPolLine}，可重新创建 ASN。
     */
    @Transactional
    public AsnNotice voidBySupplier(long supplierId, long asnId) {
        AsnNotice n = requireWithLinesForSupplier(supplierId, asnId);
        if (n.getStatus() != AsnStatus.SUBMITTED) {
            throw new BadRequestException("仅已提交的发货通知可作废");
        }
        long linked = goodsReceiptLineRepository.countByAsnNoticeId(asnId);
        if (linked > 0) {
            throw new BadRequestException("已有收货记录关联本发货通知，无法作废");
        }
        n.setStatus(AsnStatus.CANCELLED);
        return asnNoticeRepository.save(n);
    }

    private Path asnStorageRoot() {
        return Paths.get(asnUploadRoot).toAbsolutePath().normalize();
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
     * 门户上传物流单附件（单文件）。允许覆盖旧附件。
     */
    @Transactional
    public LogisticsAttachmentBrief uploadLogisticsAttachmentBySupplier(long supplierId, long asnId, MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择文件");
        }
        if (file.getSize() > MAX_ASN_LOGISTICS_ATTACHMENT_BYTES) {
            throw new BadRequestException("附件不能超过 10MB");
        }
        AsnNotice n = requireWithLinesForSupplier(supplierId, asnId);
        if (n.getStatus() != AsnStatus.SUBMITTED) {
            throw new BadRequestException("仅已提交的发货通知可上传物流单附件");
        }

        String original = sanitizeAttachmentOriginalName(file.getOriginalFilename());
        Path root = asnStorageRoot();
        Files.createDirectories(root);
        Path dir = root.resolve(String.valueOf(asnId));
        Files.createDirectories(dir);

        String ext = fileExtension(original);
        String storedFileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = dir.resolve(storedFileName);
        file.transferTo(target);
        String relative = asnId + "/" + storedFileName;

        // best-effort cleanup old file (if any)
        String old = n.getLogisticsAttachmentStoredPath();
        if (old != null && !old.isBlank()) {
            try {
                Path oldAbs = asnStorageRoot().resolve(old).normalize();
                if (oldAbs.startsWith(asnStorageRoot()) && Files.isRegularFile(oldAbs)) {
                    Files.deleteIfExists(oldAbs);
                }
            } catch (Exception ignored) {
            }
        }

        n.setLogisticsAttachmentOriginalName(original);
        n.setLogisticsAttachmentContentType(file.getContentType());
        n.setLogisticsAttachmentFileSize(file.getSize());
        n.setLogisticsAttachmentStoredPath(relative);
        asnNoticeRepository.save(n);

        return new LogisticsAttachmentBrief(
                Objects.requireNonNullElse(n.getLogisticsAttachmentOriginalName(), original),
                n.getLogisticsAttachmentContentType(),
                Objects.requireNonNullElse(n.getLogisticsAttachmentFileSize(), file.getSize())
        );
    }

    @Transactional(readOnly = true)
    public LogisticsAttachmentDownload openLogisticsAttachmentDownloadBySupplier(long supplierId, long asnId) {
        AsnNotice n = requireWithLinesForSupplier(supplierId, asnId);
        String stored = n.getLogisticsAttachmentStoredPath();
        if (stored == null || stored.isBlank()) {
            throw new NotFoundException("物流单附件不存在");
        }
        Path abs = asnStorageRoot().resolve(stored).normalize();
        Path base = asnStorageRoot().normalize();
        if (!abs.startsWith(base)) {
            throw new NotFoundException("附件路径非法");
        }
        if (!Files.isRegularFile(abs)) {
            throw new NotFoundException("文件已丢失");
        }
        Resource resource = new FileSystemResource(abs.toFile());
        return new LogisticsAttachmentDownload(resource, n.getLogisticsAttachmentOriginalName(), n.getLogisticsAttachmentContentType());
    }

    public record AsnLineInput(long purchaseOrderLineId, BigDecimal shipQty) {}

    public record LogisticsAttachmentBrief(String originalName, String contentType, long fileSize) {}

    public record LogisticsAttachmentDownload(Resource resource, String originalFileName, String contentType) {}
}
