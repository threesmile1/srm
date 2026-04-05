package com.srm.po.service;

import com.srm.foundation.domain.OrgUnit;
import com.srm.po.domain.PoNumberSeq;
import com.srm.po.domain.PoNumberSeqId;
import com.srm.po.repo.PoNumberSeqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class PoNumberService {

    private final PoNumberSeqRepository poNumberSeqRepository;

    /**
     * 生成全局唯一 PO 号，格式：{采购组织编码}-PO{年}-{序号}
     */
    @Transactional
    public String nextPoNo(OrgUnit procurementOrg) {
        int year = Year.now().getValue();
        Long oid = procurementOrg.getId();
        PoNumberSeqId id = new PoNumberSeqId(oid, year);
        if (poNumberSeqRepository.findById(id).isEmpty()) {
            PoNumberSeq init = new PoNumberSeq();
            init.setId(id);
            init.setSeqValue(0);
            poNumberSeqRepository.saveAndFlush(init);
        }
        PoNumberSeq seq = poNumberSeqRepository.findForUpdate(oid, year)
                .orElseThrow(() -> new IllegalStateException("po_number_seq missing after init"));
        long next = seq.getSeqValue() + 1;
        seq.setSeqValue(next);
        poNumberSeqRepository.save(seq);
        return procurementOrg.getCode() + "-PO" + year + "-" + String.format("%05d", next);
    }
}
