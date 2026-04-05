package com.srm.execution.service;

import com.srm.execution.domain.GrNumberSeq;
import com.srm.execution.domain.GrNumberSeqId;
import com.srm.execution.repo.GrNumberSeqRepository;
import com.srm.foundation.domain.OrgUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class GrNumberAllocator {

    private final GrNumberSeqRepository grNumberSeqRepository;

    @Transactional
    public String nextGrNo(OrgUnit procurementOrg) {
        int year = Year.now().getValue();
        Long oid = procurementOrg.getId();
        GrNumberSeqId id = new GrNumberSeqId(oid, year);
        if (grNumberSeqRepository.findById(id).isEmpty()) {
            GrNumberSeq init = new GrNumberSeq();
            init.setId(id);
            init.setLastValue(0);
            grNumberSeqRepository.saveAndFlush(init);
        }
        GrNumberSeq seq = grNumberSeqRepository.findForUpdate(oid, year).orElseThrow();
        long next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        grNumberSeqRepository.save(seq);
        return procurementOrg.getCode() + "-GR" + year + "-" + String.format("%05d", next);
    }
}
