package com.srm.perf.web;

import com.srm.perf.domain.*;
import com.srm.perf.service.PerfService;
import com.srm.perf.service.PerfService.ScoreInput;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "SupplierPerformance", description = "供应商绩效考核")
@RestController
@RequestMapping("/api/v1/perf")
@RequiredArgsConstructor
public class PerfController {

    private final PerfService perfService;

    // --- Templates ---

    @GetMapping("/templates")
    public List<TemplateResponse> listTemplates() {
        return perfService.listTemplates().stream().map(TemplateResponse::from).toList();
    }

    @GetMapping("/templates/{id}")
    public TemplateDetailResponse getTemplate(@PathVariable Long id) {
        return TemplateDetailResponse.from(perfService.getTemplate(id));
    }

    // --- Evaluations ---

    @GetMapping("/evaluations")
    public List<EvalSummary> listEvaluations(@RequestParam(required = false) Long supplierId) {
        List<PerfEvaluation> list = supplierId != null
                ? perfService.listBySupplier(supplierId)
                : perfService.listEvaluations();
        return list.stream().map(EvalSummary::from).toList();
    }

    @GetMapping("/evaluations/{id}")
    public EvalDetail getEvaluation(@PathVariable Long id) {
        return EvalDetail.from(perfService.getEvaluation(id));
    }

    @PostMapping("/evaluations")
    public EvalDetail createEvaluation(@Valid @RequestBody EvalCreateRequest req) {
        List<ScoreInput> scores = req.scores().stream()
                .map(s -> new ScoreInput(s.dimensionId(), s.score(), s.comment())).toList();
        PerfEvaluation eval = perfService.createEvaluation(
                req.supplierId(), req.templateId(), req.period(),
                req.evaluatorName(), req.remark(), scores);
        return EvalDetail.from(perfService.getEvaluation(eval.getId()));
    }

    @PostMapping("/evaluations/{id}/submit")
    public EvalDetail submit(@PathVariable Long id) {
        perfService.submitEvaluation(id);
        return EvalDetail.from(perfService.getEvaluation(id));
    }

    @PostMapping("/evaluations/{id}/publish")
    public EvalDetail publish(@PathVariable Long id) {
        perfService.publishEvaluation(id);
        return EvalDetail.from(perfService.getEvaluation(id));
    }

    // --- DTOs ---

    public record EvalCreateRequest(
            @NotNull Long supplierId,
            @NotNull Long templateId,
            @NotNull String period,
            String evaluatorName,
            String remark,
            @NotEmpty List<ScoreReq> scores
    ) {}

    public record ScoreReq(@NotNull Long dimensionId, @NotNull BigDecimal score, String comment) {}

    public record TemplateResponse(Long id, String name, String description, boolean enabled) {
        static TemplateResponse from(PerfTemplate t) {
            return new TemplateResponse(t.getId(), t.getName(), t.getDescription(), t.isEnabled());
        }
    }

    public record TemplateDetailResponse(Long id, String name, String description, boolean enabled,
                                          List<DimensionResponse> dimensions) {
        static TemplateDetailResponse from(PerfTemplate t) {
            return new TemplateDetailResponse(t.getId(), t.getName(), t.getDescription(), t.isEnabled(),
                    t.getDimensions().stream().map(DimensionResponse::from).toList());
        }
    }

    public record DimensionResponse(Long id, String name, BigDecimal weight, String description, int sortOrder) {
        static DimensionResponse from(PerfDimension d) {
            return new DimensionResponse(d.getId(), d.getName(), d.getWeight(), d.getDescription(), d.getSortOrder());
        }
    }

    public record EvalSummary(Long id, Long supplierId, String supplierCode, String supplierName,
                               String period, BigDecimal totalScore, String grade,
                               String status, String evaluatorName) {
        static EvalSummary from(PerfEvaluation e) {
            return new EvalSummary(e.getId(), e.getSupplier().getId(),
                    e.getSupplier().getCode(), e.getSupplier().getName(),
                    e.getPeriod(), e.getTotalScore(), e.getGrade(),
                    e.getStatus().name(), e.getEvaluatorName());
        }
    }

    public record EvalDetail(Long id, Long supplierId, String supplierCode, String supplierName,
                              Long templateId, String templateName, String period,
                              BigDecimal totalScore, String grade, String evaluatorName,
                              String status, String remark, List<ScoreResponse> scores) {
        static EvalDetail from(PerfEvaluation e) {
            return new EvalDetail(e.getId(), e.getSupplier().getId(),
                    e.getSupplier().getCode(), e.getSupplier().getName(),
                    e.getTemplate().getId(), e.getTemplate().getName(),
                    e.getPeriod(), e.getTotalScore(), e.getGrade(),
                    e.getEvaluatorName(), e.getStatus().name(), e.getRemark(),
                    e.getScores().stream().map(ScoreResponse::from).toList());
        }
    }

    public record ScoreResponse(Long id, Long dimensionId, String dimensionName,
                                 BigDecimal weight, BigDecimal score, String comment) {
        static ScoreResponse from(PerfScore s) {
            return new ScoreResponse(s.getId(), s.getDimension().getId(),
                    s.getDimension().getName(), s.getDimension().getWeight(),
                    s.getScore(), s.getComment());
        }
    }
}
