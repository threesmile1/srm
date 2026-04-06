package com.srm.notification.repo;

import com.srm.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long userId);

    List<Notification> findByRecipientSupplierIdOrderByIdDesc(Long supplierId);

    long countByRecipientUserIdAndReadFalse(Long userId);

    long countByRecipientSupplierIdAndReadFalse(Long supplierId);
}
