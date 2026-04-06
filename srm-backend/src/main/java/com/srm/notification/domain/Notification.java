package com.srm.notification.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "recipient_supplier_id")
    private Long recipientSupplierId;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String content;

    @Column(nullable = false, length = 32)
    private String category = "SYSTEM";

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
