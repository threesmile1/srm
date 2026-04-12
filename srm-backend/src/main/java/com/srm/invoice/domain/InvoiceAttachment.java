package com.srm.invoice.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "invoice_attachment")
public class InvoiceAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** 相对 {@code srm.invoice-upload-dir} 的路径，如 {invoiceId}/{uuid}.pdf */
    @Column(name = "stored_path", nullable = false, length = 1000)
    private String storedPath;
}
