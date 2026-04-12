package com.srm.invoice.repo;

import com.srm.invoice.domain.InvoiceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceAttachmentRepository extends JpaRepository<InvoiceAttachment, Long> {

    Optional<InvoiceAttachment> findByIdAndInvoice_Id(Long id, Long invoiceId);
}
