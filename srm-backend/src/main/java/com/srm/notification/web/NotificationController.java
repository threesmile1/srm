package com.srm.notification.web;

import com.srm.foundation.web.AuthController;
import com.srm.notification.domain.Notification;
import com.srm.notification.service.NotificationService;
import com.srm.web.error.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Notification", description = "消息通知中心")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private static long requireSessionUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            throw new BadRequestException("未登录");
        }
        Object uid = session.getAttribute(AuthController.SESSION_USER_ID);
        if (uid instanceof Long l) {
            return l;
        }
        if (uid instanceof Number n) {
            return n.longValue();
        }
        throw new BadRequestException("未登录");
    }

    /** 当前登录用户站内消息（不信任 query 中的 userId） */
    @GetMapping
    public List<Notification> list(HttpServletRequest httpReq) {
        long userId = requireSessionUserId(httpReq);
        return notificationService.listForUser(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpServletRequest httpReq) {
        long userId = requireSessionUserId(httpReq);
        return Map.of("count", notificationService.unreadCountForUser(userId));
    }

    @PostMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id, HttpServletRequest httpReq) {
        long userId = requireSessionUserId(httpReq);
        notificationService.markAsReadForRecipient(id, userId);
    }

    @PostMapping("/mark-all-read")
    public void markAllRead(HttpServletRequest httpReq) {
        long userId = requireSessionUserId(httpReq);
        notificationService.markAllReadForUser(userId);
    }
}
