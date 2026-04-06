package com.srm.perf.web;

import com.srm.foundation.web.AuthController;
import com.srm.perf.domain.EvalStatus;
import com.srm.perf.domain.PerfEvaluation;
import com.srm.perf.service.PerfService;
import com.srm.web.error.ForbiddenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PortalPerf", description = "供应商门户 - 绩效考核（已发布）")
@RestController
@RequestMapping("/api/v1/portal/perf")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalPerfController {

    private final PerfService perfService;

    private static long requireSupplierId(HttpSession session) {
        Long sid = (Long) session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid == null) {
            throw new ForbiddenException("当前用户非供应商账号");
        }
        return sid;
    }

    @GetMapping("/evaluations")
    public List<PerfController.EvalSummary> listPublished(HttpSession session) {
        long sid = requireSupplierId(session);
        return perfService.listBySupplier(sid).stream()
                .filter(e -> e.getStatus() == EvalStatus.PUBLISHED)
                .map(PerfController.EvalSummary::from)
                .toList();
    }

    @GetMapping("/evaluations/{id}")
    public PerfController.EvalDetail getPublished(@PathVariable Long id, HttpSession session) {
        long sid = requireSupplierId(session);
        PerfEvaluation e = perfService.getEvaluation(id);
        if (!e.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看该考核");
        }
        if (e.getStatus() != EvalStatus.PUBLISHED) {
            throw new ForbiddenException("仅已发布的考核可在门户查看");
        }
        return PerfController.EvalDetail.from(e);
    }
}
