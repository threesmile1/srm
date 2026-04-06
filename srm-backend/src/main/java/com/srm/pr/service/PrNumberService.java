package com.srm.pr.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.pr.domain.PrNumberSeq;
import com.srm.pr.domain.PrNumberSeqId;
import com.srm.pr.repo.PrNumberSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class PrNumberService {

    private final PrNumberSeqRepository prNumberSeqRepository;

    @Transactional
    public String nextPrNo(OrgUnit procurementOrg) {
        int year = Year.now().getValue();
        Long oid = procurementOrg.getId();
        PrNumberSeqId id = new PrNumberSeqId(oid, year);
        if (prNumberSeqRepository.findById(id).isEmpty()) {
            PrNumberSeq init = new PrNumberSeq();
            init.setProcurementOrgId(oid);
            init.setYearVal(year);
            init.setSeqValue(0);
            prNumberSeqRepository.saveAndFlush(init);
        }
        PrNumberSeq seq = prNumberSeqRepository.findForUpdate(oid, year)
                .orElseThrow(() -> new IllegalStateException("pr_number_seq missing after init"));
        long next = seq.getSeqValue() + 1;
        seq.setSeqValue(next);
        prNumberSeqRepository.save(seq);
        return procurementOrg.getCode() + "-PR" + year + "-" + String.format("%05d", next);
    }
}
