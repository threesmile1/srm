package com.srm.perf.service;

import com.srm.foundation.service.AuditService;
import com.srm.master.domain.Supplier;
import com.srm.master.service.MasterDataService;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import com.srm.perf.domain.*;
import com.srm.perf.repo.PerfEvaluationRepository;
import com.srm.perf.repo.PerfTemplateRepository;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfService {

    private final PerfTemplateRepository templateRepository;
    private final PerfEvaluationRepository evaluationRepository;
    private final MasterDataService masterDataService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    // --- Templates ---

    @Transactional(readOnly = true)
    public List<PerfTemplate> listTemplates() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PerfTemplate getTemplate(Long id) {
        return templateRepository.findWithDimensionsById(id)
                .orElseThrow(() -> new NotFoundException("考核模板不存在: " + id));
    }

    @Transactional
    public PerfTemplate saveTemplate(PerfTemplate template) {
        return templateRepository.save(template);
    }

    // --- Evaluations ---

    @Transactional(readOnly = true)
    public List<PerfEvaluation> listEvaluations() {
        return evaluationRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public List<PerfEvaluation> listBySupplier(Long supplierId) {
        return evaluationRepository.findBySupplierIdOrderByIdDesc(supplierId);
    }

    @Transactional(readOnly = true)
    public PerfEvaluation getEvaluation(Long id) {
        return evaluationRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("考核记录不存在: " + id));
    }

    @Transactional
    public PerfEvaluation createEvaluation(Long supplierId, Long templateId, String period,
                                            String evaluatorName, String remark,
                                            List<ScoreInput> scoreInputs) {
        Supplier supplier = masterDataService.requireSupplier(supplierId);
        PerfTemplate template = getTemplate(templateId);

        PerfEvaluation eval = new PerfEvaluation();
        eval.setSupplier(supplier);
        eval.setTemplate(template);
        eval.setPeriod(period);
        eval.setEvaluatorName(evaluatorName);
        eval.setStatus(EvalStatus.DRAFT);
        eval.setRemark(remark);

        Map<Long, PerfDimension> dimMap = template.getDimensions().stream()
                .collect(Collectors.toMap(PerfDimension::getId, d -> d));

        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        for (ScoreInput si : scoreInputs) {
            PerfDimension dim = dimMap.get(si.dimensionId());
            if (dim == null) throw new BadRequestException("维度不存在: " + si.dimensionId());

            PerfScore ps = new PerfScore();
            ps.setEvaluation(eval);
            ps.setDimension(dim);
            ps.setScore(si.score());
            ps.setComment(si.comment());
            eval.getScores().add(ps);

            totalWeightedScore = totalWeightedScore.add(
                    si.score().multiply(dim.getWeight()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }

        eval.setTotalScore(totalWeightedScore);
        eval.setGrade(computeGrade(totalWeightedScore));

        PerfEvaluation saved = evaluationRepository.save(eval);
        auditService.log(null, null, "CREATE_EVAL", "PERF_EVAL", saved.getId(),
                "supplier=" + supplier.getCode() + " period=" + period + " score=" + totalWeightedScore, null);
        return saved;
    }

    @Transactional
    public PerfEvaluation submitEvaluation(Long id) {
        PerfEvaluation eval = getEvaluation(id);
        if (eval.getStatus() != EvalStatus.DRAFT) {
            throw new BadRequestException("仅草稿可提交");
        }
        eval.setStatus(EvalStatus.SUBMITTED);
        return evaluationRepository.save(eval);
    }

    @Transactional
    public PerfEvaluation publishEvaluation(Long id) {
        PerfEvaluation eval = getEvaluation(id);
        if (eval.getStatus() != EvalStatus.SUBMITTED) {
            throw new BadRequestException("仅已提交可发布");
        }
        eval.setStatus(EvalStatus.PUBLISHED);
        auditService.log(null, null, "PUBLISH_EVAL", "PERF_EVAL", id,
                "supplier=" + eval.getSupplier().getCode(), null);
        PerfEvaluation saved = evaluationRepository.save(eval);
        Supplier supplier = saved.getSupplier();
        String supplierMsg = String.format(
                "周期 %s 绩效考核已发布，模板「%s」，得分 %s，等级 %s。",
                saved.getPeriod(),
                saved.getTemplate().getName(),
                saved.getTotalScore(),
                saved.getGrade() != null ? saved.getGrade() : "-");
        try {
            notificationService.send(
                    null,
                    supplier.getId(),
                    "绩效考核已发布",
                    supplierMsg,
                    "PERF_PUBLISHED",
                    "PERF_EVAL",
                    saved.getId());
        } catch (Exception e) {
            log.warn("绩效发布通知（供应商）失败: {}", e.getMessage());
        }
        try {
            staffNotificationService.notifyInternalForSupplierScopedOrgs(
                    supplier,
                    "绩效考核已发布",
                    "供应商 " + supplier.getName() + "：" + supplierMsg,
                    "PERF_PUBLISHED_INTERNAL",
                    "PERF_EVAL",
                    saved.getId());
        } catch (Exception e) {
            log.warn("绩效发布通知（内部）失败: {}", e.getMessage());
        }
        return saved;
    }

    private String computeGrade(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return "A";
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) return "B";
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return "C";
        return "D";
    }

    public record ScoreInput(Long dimensionId, BigDecimal score, String comment) {}
}
