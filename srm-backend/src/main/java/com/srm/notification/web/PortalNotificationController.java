package com.srm.notification.web;

import com.srm.foundation.web.AuthController;
import com.srm.notification.domain.Notification;
import com.srm.notification.service.NotificationService;
import com.srm.web.error.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "PortalNotification", description = "供应商门户 - 消息通知")
@RestController
@RequestMapping("/api/v1/portal/notifications")
@RequiredArgsConstructor
public class PortalNotificationController {

    private final NotificationService notificationService;

    private static long requireSupplierId(HttpSession session) {
        if (session == null) {
            throw new BadRequestException("未登录");
        }
        Object sid = session.getAttribute(AuthController.SESSION_SUPPLIER_ID);
        if (sid instanceof Long l) {
            return l;
        }
        if (sid instanceof Number n) {
            return n.longValue();
        }
        throw new BadRequestException("当前会话非供应商门户账号");
    }

    @GetMapping
    public List<Notification> list(HttpSession session) {
        return notificationService.listForSupplier(requireSupplierId(session));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpSession session) {
        return Map.of("count", notificationService.unreadCountForSupplier(requireSupplierId(session)));
    }

    @PostMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id, HttpSession session) {
        notificationService.markAsReadForSupplier(id, requireSupplierId(session));
    }

    @PostMapping("/mark-all-read")
    public void markAllRead(HttpSession session) {
        notificationService.markAllReadForSupplier(requireSupplierId(session));
    }
}
