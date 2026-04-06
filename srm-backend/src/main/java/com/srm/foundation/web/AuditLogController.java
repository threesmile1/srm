package com.srm.foundation.web;

import com.srm.foundation.domain.AuditLog;
import com.srm.foundation.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AuditLog", description = "审计日志查询")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return auditService.query(entityType, entityId, PageRequest.of(page, Math.min(size, 200)))
                .map(AuditLogResponse::from);
    }

    public record AuditLogResponse(
            Long id, Long userId, String username, String action,
            String entityType, Long entityId, String detail, String ipAddress, String createdAt
    ) {
        static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(
                    a.getId(), a.getUserId(), a.getUsername(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getDetail(), a.getIpAddress(),
                    a.getCreatedAt() != null ? a.getCreatedAt().toString() : null
            );
        }
    }
}
