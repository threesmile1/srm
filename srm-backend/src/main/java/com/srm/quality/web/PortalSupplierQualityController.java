package com.srm.quality.web;

import com.srm.foundation.web.AuthController;
import com.srm.quality.domain.CorrectiveAction;
import com.srm.quality.domain.QualityInspection;
import com.srm.quality.repo.CorrectiveActionRepository;
import com.srm.quality.repo.QualityInspectionRepository;
import com.srm.web.error.ForbiddenException;
import com.srm.web.error.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PortalQuality", description = "供应商门户 - 质量协同只读")
@RestController
@RequestMapping("/api/v1/portal/quality")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalSupplierQualityController {

    private final QualityInspectionRepository inspectionRepository;
    private final CorrectiveActionRepository correctiveActionRepository;

    private static long requireSupplierId(HttpSession session) {
        Long sid = (Long) session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid == null) {
            throw new ForbiddenException("当前用户非供应商账号");
        }
        return sid;
    }

    @GetMapping("/inspections")
    public List<QualityController.InspectionResponse> listInspections(HttpSession session) {
        long sid = requireSupplierId(session);
        return inspectionRepository.findBySupplierIdOrderByIdDesc(sid).stream()
                .map(QualityController.InspectionResponse::from)
                .toList();
    }

    @GetMapping("/inspections/{id}")
    public QualityController.InspectionResponse getInspection(@PathVariable Long id, HttpSession session) {
        long sid = requireSupplierId(session);
        QualityInspection qi = inspectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("质检单不存在: " + id));
        if (!qi.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看该质检单");
        }
        return QualityController.InspectionResponse.from(qi);
    }

    @GetMapping("/corrective-actions")
    public List<QualityController.CorrectiveActionResponse> listCorrectiveActions(HttpSession session) {
        long sid = requireSupplierId(session);
        return correctiveActionRepository.findBySupplierIdOrderByIdDesc(sid).stream()
                .map(QualityController.CorrectiveActionResponse::from)
                .toList();
    }

    @GetMapping("/corrective-actions/{id}")
    public QualityController.CorrectiveActionResponse getCorrectiveAction(@PathVariable Long id,
                                                                          HttpSession session) {
        long sid = requireSupplierId(session);
        CorrectiveAction ca = correctiveActionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("纠正措施不存在: " + id));
        if (!ca.getSupplier().getId().equals(sid)) {
            throw new ForbiddenException("无权查看该纠正措施");
        }
        return QualityController.CorrectiveActionResponse.from(ca);
    }
}
