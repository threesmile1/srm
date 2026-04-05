package com.srm.execution.service;

import com.srm.execution.domain.AsnNumberSeq;
import com.srm.execution.domain.AsnNumberSeqId;
import com.srm.execution.repo.AsnNumberSeqRepository;
import com.srm.foundation.domain.OrgUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class AsnNumberAllocator {

    private final AsnNumberSeqRepository asnNumberSeqRepository;

    @Transactional
    public String nextAsnNo(OrgUnit procurementOrg) {
        int year = Year.now().getValue();
        Long oid = procurementOrg.getId();
        AsnNumberSeqId id = new AsnNumberSeqId(oid, year);
        if (asnNumberSeqRepository.findById(id).isEmpty()) {
            AsnNumberSeq init = new AsnNumberSeq();
            init.setId(id);
            init.setLastValue(0);
            asnNumberSeqRepository.saveAndFlush(init);
        }
        AsnNumberSeq seq = asnNumberSeqRepository.findForUpdate(oid, year).orElseThrow();
        long next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        asnNumberSeqRepository.save(seq);
        return procurementOrg.getCode() + "-ASN" + year + "-" + String.format("%05d", next);
    }
}
