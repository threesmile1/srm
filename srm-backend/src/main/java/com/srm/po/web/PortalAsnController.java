package com.srm.po.web;

import com.srm.execution.service.AsnService;
import com.srm.execution.web.AsnNoticeResponse;
import com.srm.foundation.web.PortalSupplierSession;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "PortalAsn", description = "A5 门户发货通知")
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalAsnController {

    private final AsnService asnService;

    @Transactional(readOnly = true)
    @GetMapping("/asn-notices")
    public List<AsnNoticeResponse> list(
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        return asnService.listForSupplier(sid).stream()
                .map(AsnNoticeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/asn-notices/{id}")
    public AsnNoticeResponse get(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        return AsnNoticeResponse.from(asnService.requireWithLinesForSupplier(sid, id));
    }

    @PostMapping("/asn-notices")
    public AsnNoticeResponse create(
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId,
            @Valid @RequestBody PortalAsnCreateRequest body
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        List<AsnService.AsnLineInput> lines = body.lines().stream()
                .map(l -> new AsnService.AsnLineInput(l.purchaseOrderLineId(), l.shipQty()))
                .toList();
        var n = asnService.createFromSupplier(
                sid,
                body.purchaseOrderId(),
                body.shipDate(),
                body.etaDate(),
                body.carrier(),
                body.trackingNo(),
                body.remark(),
                body.receiverName(),
                body.receiverPhone(),
                body.receiverAddress(),
                lines
        );
        return AsnNoticeResponse.from(asnService.requireWithLines(n.getId()));
    }

    /** 作废发货通知：释放可发货占用；若已有收货关联则不允许。 */
    @PostMapping("/asn-notices/{id}/void")
    public AsnNoticeResponse voidNotice(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        return AsnNoticeResponse.from(asnService.voidBySupplier(sid, id));
    }

    /** 上传物流单附件（单文件 ≤10MB，可覆盖上传） */
    @PostMapping("/asn-notices/{id}/logistics-attachment")
    public LogisticsAttachmentResponse uploadLogisticsAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) throws IOException {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        var b = asnService.uploadLogisticsAttachmentBySupplier(sid, id, file);
        return new LogisticsAttachmentResponse(b.originalName(), b.contentType(), b.fileSize());
    }

    @GetMapping("/asn-notices/{id}/logistics-attachment/file")
    public ResponseEntity<Resource> downloadLogisticsAttachment(
            @PathVariable Long id,
            HttpSession session,
            @RequestHeader(value = "X-Dev-Supplier-Id", required = false) Long headerSupplierId,
            @RequestParam(value = "supplierId", required = false) Long querySupplierId
    ) {
        long sid = PortalSupplierSession.resolveSupplierId(session, headerSupplierId, querySupplierId);
        var d = asnService.openLogisticsAttachmentDownloadBySupplier(sid, id);
        return attachmentResponse(d);
    }

    private static ResponseEntity<Resource> attachmentResponse(AsnService.LogisticsAttachmentDownload d) {
        HttpHeaders headers = new HttpHeaders();
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (d.contentType() != null && !d.contentType().isBlank()) {
            try {
                mt = MediaType.parseMediaType(d.contentType());
            } catch (Exception ignored) {
            }
        }
        headers.setContentType(mt);
        String fname = d.originalFileName() != null ? d.originalFileName() : "attachment";
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fname, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(d.resource());
    }

    public record PortalAsnCreateRequest(
            @NotNull Long purchaseOrderId,
            @NotNull LocalDate shipDate,
            LocalDate etaDate,
            String carrier,
            String trackingNo,
            String remark,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            @NotEmpty List<PortalAsnLineReq> lines
    ) {
        public record PortalAsnLineReq(
                @NotNull Long purchaseOrderLineId,
                @NotNull BigDecimal shipQty
        ) {}
    }

    public record LogisticsAttachmentResponse(String originalName, String contentType, long fileSize) {}
}
