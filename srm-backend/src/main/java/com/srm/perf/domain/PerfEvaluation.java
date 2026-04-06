package com.srm.perf.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.master.domain.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "perf_evaluation")
public class PerfEvaluation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private PerfTemplate template;

    @Column(nullable = false, length = 32)
    private String period;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(length = 16)
    private String grade;

    @Column(name = "evaluator_name", length = 128)
    private String evaluatorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvalStatus status = EvalStatus.DRAFT;

    @Column(length = 1000)
    private String remark;

    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PerfScore> scores = new ArrayList<>();
}
