package com.srm.rfq.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.OrgUnitType;
import com.srm.foundation.repo.OrgUnitRepository;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.rfq.domain.Rfq;
import com.srm.rfq.domain.RfqInvitation;
import com.srm.rfq.domain.RfqLine;
import com.srm.rfq.domain.RfqQuotation;
import com.srm.rfq.domain.RfqQuotationLine;
import com.srm.rfq.domain.RfqStatus;
import com.srm.notification.service.NotificationService;
import com.srm.rfq.repo.RfqQuotationRepository;
import com.srm.rfq.repo.RfqRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RfqService {

    private final RfqRepository rfqRepository;
    private final RfqQuotationRepository rfqQuotationRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final MasterDataService masterDataService;
    private final NotificationService notificationService;

    private final AtomicLong rfqSeq = new AtomicLong(System.currentTimeMillis() % 100000);

    @Transactional
    public Rfq create(Long orgId, String title, LocalDate deadline, String remark,
                      List<CreateRfqLine> lines, List<Long> supplierIds) {
        OrgUnit org = orgUnitRepository.findById(orgId)
                .orElseThrow(() -> new NotFoundException("采购组织不存在: " + orgId));
        if (org.getOrgType() != OrgUnitType.PROCUREMENT) {
            throw new BadRequestException("请选择采购组织");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestException("询价单至少一行");
        }

        String rfqNo = "RFQ" + Year.now().getValue() + "-" + String.format("%05d", rfqSeq.incrementAndGet());

        Rfq rfq = new Rfq();
        rfq.setRfqNo(rfqNo);
        rfq.setTitle(title);
        rfq.setProcurementOrg(org);
        rfq.setStatus(RfqStatus.DRAFT);
        rfq.setDeadline(deadline);
        rfq.setRemark(remark);

        int n = 1;
        for (CreateRfqLine cl : lines) {
            MaterialItem mat = masterDataService.requireMaterial(cl.materialId());
            RfqLine line = new RfqLine();
            line.setRfq(rfq);
            line.setLineNo(n++);
            line.setMaterial(mat);
            line.setQty(cl.qty());
            line.setUom(cl.uom() != null && !cl.uom().isBlank() ? cl.uom() : mat.getUom());
            line.setSpecification(cl.specification());
            line.setRemark(cl.remark());
            rfq.getLines().add(line);
        }

        if (supplierIds != null) {
            for (Long sid : supplierIds) {
                Supplier supplier = masterDataService.requireSupplier(sid);
                RfqInvitation inv = new RfqInvitation();
                inv.setRfq(rfq);
                inv.setSupplier(supplier);
                rfq.getInvitations().add(inv);
            }
        }

        return rfqRepository.save(rfq);
    }

    @Transactional
    public Rfq publish(Long id) {
        Rfq rfq = requireDetail(id);
        if (rfq.getStatus() != RfqStatus.DRAFT) {
            throw new BadRequestException("仅草稿可发布，当前状态: " + rfq.getStatus());
        }
        if (rfq.getInvitations().isEmpty()) {
            throw new BadRequestException("至少邀请一个供应商");
        }
        rfq.setStatus(RfqStatus.PUBLISHED);
        rfq.setPublishDate(LocalDate.now());
        Rfq saved = rfqRepository.save(rfq);
        for (RfqInvitation inv : saved.getInvitations()) {
            try {
                notificationService.send(
                        null,
                        inv.getSupplier().getId(),
                        "新询价邀请",
                        "询价单 " + saved.getRfqNo() + " 已发布，请在门户提交报价。",
                        "RFQ_PUBLISHED",
                        "RFQ",
                        saved.getId());
            } catch (Exception e) {
                log.warn("RFQ 发布后写入供应商通知失败 supplierId={}: {}", inv.getSupplier().getId(), e.getMessage());
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Rfq requireDetail(Long id) {
        return rfqRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("询价单不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<Rfq> listByOrg(Long orgId) {
        return rfqRepository.findByProcurementOrgIdOrderByIdDesc(orgId);
    }

    @Transactional(readOnly = true)
    public List<RfqQuotation> listQuotations(Long rfqId) {
        return rfqQuotationRepository.findByRfqIdOrderByIdDesc(rfqId);
    }

    @Transactional
    public RfqQuotation submitQuotation(Long rfqId, Long supplierId, String currency,
                                        Integer deliveryDays, Integer validityDays,
                                        String remark, List<QuotLineInput> lines) {
        Rfq rfq = requireDetail(rfqId);
        if (rfq.getStatus() != RfqStatus.PUBLISHED) {
            throw new BadRequestException("询价单未处于已发布状态，无法报价");
        }
        if (rfq.getDeadline() != null && LocalDate.now().isAfter(rfq.getDeadline())) {
            throw new BadRequestException("已超过报价截止日期");
        }

        boolean invited = rfq.getInvitations().stream()
                .anyMatch(inv -> inv.getSupplier().getId().equals(supplierId));
        if (!invited) {
            throw new BadRequestException("供应商未被邀请参与此询价");
        }

        if (rfqQuotationRepository.findByRfqIdAndSupplierId(rfqId, supplierId).isPresent()) {
            throw new BadRequestException("该供应商已提交报价，不可重复提交");
        }

        Supplier supplier = masterDataService.requireSupplier(supplierId);

        Map<Long, RfqLine> rfqLineMap = rfq.getLines().stream()
                .collect(Collectors.toMap(RfqLine::getId, Function.identity()));

        RfqQuotation quotation = new RfqQuotation();
        quotation.setRfq(rfq);
        quotation.setSupplier(supplier);
        quotation.setCurrency(currency != null && !currency.isBlank() ? currency : "CNY");
        quotation.setDeliveryDays(deliveryDays);
        quotation.setValidityDays(validityDays);
        quotation.setRemark(remark);
        quotation.setSubmittedAt(Instant.now());

        BigDecimal total = BigDecimal.ZERO;
        for (QuotLineInput ql : lines) {
            RfqLine rfqLine = rfqLineMap.get(ql.rfqLineId());
            if (rfqLine == null) {
                throw new BadRequestException("询价行不存在: " + ql.rfqLineId());
            }
            BigDecimal amount = ql.unitPrice().multiply(rfqLine.getQty()).setScale(4, RoundingMode.HALF_UP);
            RfqQuotationLine qLine = new RfqQuotationLine();
            qLine.setQuotation(quotation);
            qLine.setRfqLine(rfqLine);
            qLine.setUnitPrice(ql.unitPrice());
            qLine.setAmount(amount);
            qLine.setRemark(ql.remark());
            quotation.getQuotationLines().add(qLine);
            total = total.add(amount);
        }
        quotation.setTotalAmount(total);

        rfq.getInvitations().stream()
                .filter(inv -> inv.getSupplier().getId().equals(supplierId))
                .findFirst()
                .ifPresent(inv -> inv.setResponded(true));
        rfqRepository.save(rfq);

        return rfqQuotationRepository.save(quotation);
    }

    @Transactional
    public Rfq award(Long rfqId, Long winningSupplierId) {
        Rfq rfq = requireDetail(rfqId);
        if (rfq.getStatus() != RfqStatus.PUBLISHED && rfq.getStatus() != RfqStatus.EVALUATING) {
            throw new BadRequestException("仅已发布或评估中的询价单可定标，当前状态: " + rfq.getStatus());
        }
        rfqQuotationRepository.findByRfqIdAndSupplierId(rfqId, winningSupplierId)
                .orElseThrow(() -> new BadRequestException("中标供应商尚未提交报价"));
        rfq.setStatus(RfqStatus.AWARDED);
        Rfq saved = rfqRepository.save(rfq);
        try {
            notificationService.send(
                    null,
                    winningSupplierId,
                    "询价已定标",
                    "询价单 " + saved.getRfqNo() + " 已定标，贵司为中标供应商。",
                    "RFQ_AWARDED",
                    "RFQ",
                    saved.getId());
        } catch (Exception e) {
            log.warn("RFQ 定标后写入中标供应商通知失败: {}", e.getMessage());
        }
        return saved;
    }

    public record CreateRfqLine(Long materialId, BigDecimal qty, String uom, String specification, String remark) {}

    public record QuotLineInput(Long rfqLineId, BigDecimal unitPrice, String remark) {}
}
