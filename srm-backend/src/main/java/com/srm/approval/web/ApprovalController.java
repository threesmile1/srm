package com.srm.approval.web;

import com.srm.approval.domain.*;
import com.srm.approval.service.ApprovalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Tag(name = "Approval", description = "审批引擎")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    // --- Rules management ---

    @GetMapping("/rules")
    public List<RuleResponse> listRules() {
        return approvalService.listRules().stream().map(RuleResponse::from).toList();
    }

    @PostMapping("/rules")
    public RuleResponse saveRule(@Valid @RequestBody RuleSaveRequest req) {
        ApprovalRule rule = new ApprovalRule();
        if (req.id() != null) {
            rule = approvalService.listRules().stream()
                    .filter(r -> r.getId().equals(req.id())).findFirst().orElse(rule);
        }
        rule.setDocType(req.docType());
        rule.setMinAmount(req.minAmount() != null ? req.minAmount() : BigDecimal.ZERO);
        rule.setMaxAmount(req.maxAmount());
        rule.setApprovalLevel(req.approvalLevel());
        rule.setApproverRole(req.approverRole());
        rule.setDescription(req.description());
        rule.setEnabled(req.enabled() == null || req.enabled());
        return RuleResponse.from(approvalService.saveRule(rule));
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        approvalService.deleteRule(id);
    }

    // --- Instances ---

    @GetMapping("/instances")
    public List<InstanceSummary> listInstances(@RequestParam(required = false) String status) {
        List<ApprovalInstance> list = (status != null && !status.isBlank())
                ? approvalService.listPending()
                : approvalService.listAll();
        return list.stream().map(InstanceSummary::from).toList();
    }

    @GetMapping("/instances/{id}")
    public InstanceDetail getInstance(@PathVariable Long id) {
        return InstanceDetail.from(approvalService.getInstance(id));
    }

    @GetMapping("/instances/by-doc")
    public InstanceDetail getByDoc(@RequestParam String docType, @RequestParam Long docId) {
        ApprovalInstance inst = approvalService.getInstanceByDoc(docType, docId);
        return inst != null ? InstanceDetail.from(approvalService.getInstance(inst.getId())) : null;
    }

    @PostMapping("/instances/{id}/action")
    public InstanceDetail processAction(@PathVariable Long id, @Valid @RequestBody ActionRequest req) {
        ApprovalInstance inst = approvalService.processAction(
                id, req.action(), req.approverId(), req.approverName(), req.comment());
        return InstanceDetail.from(approvalService.getInstance(inst.getId()));
    }

    // --- DTOs ---

    public record RuleSaveRequest(
            Long id,
            @NotNull String docType,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int approvalLevel,
            @NotNull String approverRole,
            String description,
            Boolean enabled
    ) {}

    public record ActionRequest(
            @NotNull StepAction action,
            Long approverId,
            String approverName,
            String comment
    ) {}

    public record RuleResponse(Long id, String docType, BigDecimal minAmount, BigDecimal maxAmount,
                                int approvalLevel, String approverRole, String description, boolean enabled) {
        static RuleResponse from(ApprovalRule r) {
            return new RuleResponse(r.getId(), r.getDocType(), r.getMinAmount(), r.getMaxAmount(),
                    r.getApprovalLevel(), r.getApproverRole(), r.getDescription(), r.isEnabled());
        }
    }

    public record InstanceSummary(Long id, String docType, Long docId, String docNo,
                                   BigDecimal totalAmount, String status, int currentLevel, String createdAt) {
        static InstanceSummary from(ApprovalInstance i) {
            return new InstanceSummary(i.getId(), i.getDocType(), i.getDocId(), i.getDocNo(),
                    i.getTotalAmount(), i.getStatus().name(), i.getCurrentLevel(),
                    i.getCreatedAt() != null ? i.getCreatedAt().toString() : null);
        }
    }

    public record InstanceDetail(Long id, String docType, Long docId, String docNo,
                                  BigDecimal totalAmount, String status, int currentLevel,
                                  List<StepResponse> steps) {
        static InstanceDetail from(ApprovalInstance i) {
            return new InstanceDetail(i.getId(), i.getDocType(), i.getDocId(), i.getDocNo(),
                    i.getTotalAmount(), i.getStatus().name(), i.getCurrentLevel(),
                    i.getSteps().stream().map(StepResponse::from).toList());
        }
    }

    public record StepResponse(Long id, int stepLevel, String approverRole,
                                Long approverId, String approverName, String action,
                                String comment, String actedAt) {
        static StepResponse from(ApprovalStep s) {
            return new StepResponse(s.getId(), s.getStepLevel(), s.getApproverRole(),
                    s.getApproverId(), s.getApproverName(),
                    s.getAction() != null ? s.getAction().name() : null,
                    s.getComment(),
                    s.getActedAt() != null ? s.getActedAt().toString() : null);
        }
    }
}
