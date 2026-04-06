package com.srm.notification.service;

import com.srm.notification.domain.Notification;
import com.srm.notification.repo.NotificationRepository;
import com.srm.web.error.ForbiddenException;
import com.srm.web.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification send(Long recipientUserId, Long recipientSupplierId,
                             String title, String content,
                             String category, String refType, Long refId) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setRecipientSupplierId(recipientSupplierId);
        n.setTitle(title);
        n.setContent(content);
        if (category != null) {
            n.setCategory(category);
        }
        n.setRefType(refType);
        n.setRefId(refId);
        return notificationRepository.save(n);
    }

    public List<Notification> listForUser(Long userId) {
        return notificationRepository.findByRecipientUserIdOrderByIdDesc(userId);
    }

    public List<Notification> listForSupplier(Long supplierId) {
        return notificationRepository.findByRecipientSupplierIdOrderByIdDesc(supplierId);
    }

    public long unreadCountForUser(Long userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    public long unreadCountForSupplier(Long supplierId) {
        return notificationRepository.countByRecipientSupplierIdAndReadFalse(supplierId);
    }

    @Transactional
    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAsReadForRecipient(Long id, Long userId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("消息不存在: " + id));
        if (!Objects.equals(n.getRecipientUserId(), userId)) {
            throw new ForbiddenException("无权标记该消息");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllReadForUser(Long userId) {
        List<Notification> unread = notificationRepository.findByRecipientUserIdOrderByIdDesc(userId)
                .stream()
                .filter(n -> !n.isRead())
                .toList();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void markAsReadForSupplier(Long id, Long supplierId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("消息不存在: " + id));
        if (!Objects.equals(n.getRecipientSupplierId(), supplierId)) {
            throw new ForbiddenException("无权标记该消息");
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllReadForSupplier(Long supplierId) {
        List<Notification> unread = notificationRepository.findByRecipientSupplierIdOrderByIdDesc(supplierId)
                .stream()
                .filter(n -> !n.isRead())
                .toList();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
