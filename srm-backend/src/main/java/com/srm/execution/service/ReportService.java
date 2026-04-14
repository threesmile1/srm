package com.srm.execution.service;

import com.srm.po.domain.PoStatus;
import com.srm.po.domain.PurchaseOrder;
import com.srm.po.domain.PurchaseOrderLine;
import com.srm.po.repo.PurchaseOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PurchaseExecutionRow> purchaseExecution(Long procurementOrgId) {
        List<PurchaseOrder> pos = purchaseOrderRepository.findWithLinesForReport(
                procurementOrgId, List.of(PoStatus.RELEASED, PoStatus.CLOSED));
        List<PurchaseExecutionRow> rows = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            for (PurchaseOrderLine l : po.getLines()) {
                BigDecimal open = l.getQty().subtract(l.getReceivedQty());
                if (open.compareTo(BigDecimal.ZERO) < 0) {
                    open = BigDecimal.ZERO;
                }
                rows.add(new PurchaseExecutionRow(
                        po.getPoNo(),
                        po.getStatus().name(),
                        po.getU9BusinessDate() != null ? po.getU9BusinessDate().toString() : null,
                        po.getU9OfficialOrderNo(),
                        po.getU9Store2(),
                        po.getU9ReceiverName(),
                        po.getU9TerminalPhone(),
                        po.getU9InstallAddress(),
                        l.getLineNo(),
                        l.getMaterial().getCode(),
                        l.getMaterial().getName(),
                        l.getQty(),
                        l.getReceivedQty(),
                        open
                ));
            }
        }
        return rows;
    }

    public record PurchaseExecutionRow(
            String poNo,
            String poStatus,
            String businessDate,
            String officialOrderNo,
            String store2,
            String receiverName,
            String terminalPhone,
            String installAddress,
            int lineNo,
            String materialCode,
            String materialName,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal openQty
    ) {}

    @Transactional(readOnly = true)
    public List<MonthAmountRow> purchaseAmountTrend(Long procurementOrgId, int months) {
        int n = Math.min(Math.max(months, 1), 24);
        List<MonthAmountRow> result = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = n - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            BigDecimal amt = purchaseOrderRepository.sumAmountBySupplierAndOrgAndPeriod(
                    null, procurementOrgId, from, to);
            result.add(new MonthAmountRow(ym.toString(), amt != null ? amt : BigDecimal.ZERO));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SupplierShareRow> supplierShare(Long procurementOrgId, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Object[]> raw = purchaseOrderRepository.sumLineAmountGroupedBySupplier(
                procurementOrgId,
                List.of(PoStatus.DRAFT, PoStatus.CANCELLED),
                start,
                toExclusive);
        BigDecimal total = raw.stream()
                .map(r -> toBd(r[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return raw.stream()
                    .map(r -> new SupplierShareRow((String) r[0], (String) r[1],
                            toBd(r[2]), BigDecimal.ZERO))
                    .toList();
        }
        return raw.stream()
                .map(r -> {
                    BigDecimal amt = toBd(r[2]);
                    BigDecimal pct = amt.multiply(BigDecimal.valueOf(100))
                            .divide(total, 2, RoundingMode.HALF_UP);
                    return new SupplierShareRow((String) r[0], (String) r[1], amt, pct);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public DeliveryAchievementRow deliveryAchievement(Long procurementOrgId) {
        Query completed = entityManager.createNativeQuery("""
                select
                    coalesce(sum(case when max_rd <= due_d then 1 else 0 end), 0),
                    coalesce(sum(case when max_rd > due_d then 1 else 0 end), 0)
                from (
                    select pol.id as lid,
                           coalesce(pol.promised_date, pol.requested_date) as due_d,
                           max(gr.receipt_date) as max_rd
                    from purchase_order_line pol
                    inner join purchase_order po on po.id = pol.purchase_order_id
                    left join goods_receipt_line grl on grl.purchase_order_line_id = pol.id
                    left join goods_receipt gr on gr.id = grl.goods_receipt_id
                    where po.procurement_org_id = :oid
                      and po.status in ('RELEASED', 'CLOSED')
                      and coalesce(pol.promised_date, pol.requested_date) is not null
                      and pol.received_qty >= pol.qty
                      and pol.qty > 0
                    group by pol.id, coalesce(pol.promised_date, pol.requested_date)
                ) t
                where max_rd is not null
                """);
        completed.setParameter("oid", procurementOrgId);
        Object[] cr = (Object[]) completed.getSingleResult();
        Number onTimeN = (Number) cr[0];
        Number lateN = (Number) cr[1];
        int onTime = onTimeN != null ? onTimeN.intValue() : 0;
        int late = lateN != null ? lateN.intValue() : 0;

        Query openQ = entityManager.createNativeQuery("""
                select count(*)
                from purchase_order_line pol
                inner join purchase_order po on po.id = pol.purchase_order_id
                where po.procurement_org_id = :oid
                  and po.status in ('RELEASED', 'CLOSED')
                  and coalesce(pol.promised_date, pol.requested_date) is not null
                  and pol.received_qty < pol.qty
                """);
        openQ.setParameter("oid", procurementOrgId);
        Number openN = (Number) openQ.getSingleResult();
        int openWithDue = openN != null ? openN.intValue() : 0;

        int finished = onTime + late;
        BigDecimal rate = finished > 0
                ? BigDecimal.valueOf(onTime).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(finished), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new DeliveryAchievementRow(onTime, late, openWithDue, rate);
    }

    @Transactional(readOnly = true)
    public List<PriceAnalysisRow> priceAnalysis(Long procurementOrgId, LocalDate from, int limit) {
        int lim = Math.min(Math.max(limit, 1), 100);
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Query q = entityManager.createNativeQuery("""
                select m.code, m.name,
                    min(pol.unit_price), max(pol.unit_price), avg(pol.unit_price),
                    count(*), coalesce(sum(pol.amount), 0)
                from purchase_order_line pol
                inner join purchase_order po on po.id = pol.purchase_order_id
                inner join material_item m on m.id = pol.material_id
                where po.procurement_org_id = :oid
                  and po.status not in ('DRAFT', 'CANCELLED')
                  and po.created_at >= :fromTs
                group by m.id, m.code, m.name
                having count(*) >= 2
                order by sum(pol.amount) desc
                limit :lim
                """);
        q.setParameter("oid", procurementOrgId);
        q.setParameter("fromTs", Timestamp.from(fromInstant));
        q.setParameter("lim", lim);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<PriceAnalysisRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            BigDecimal minP = toBd(r[2]);
            BigDecimal maxP = toBd(r[3]);
            BigDecimal avgP = toBd(r[4]);
            long lineCount = ((Number) r[5]).longValue();
            BigDecimal totalAmt = toBd(r[6]);
            BigDecimal volatilityPct = BigDecimal.ZERO;
            if (avgP != null && avgP.compareTo(BigDecimal.ZERO) > 0 && minP != null && maxP != null) {
                volatilityPct = maxP.subtract(minP).multiply(BigDecimal.valueOf(100))
                        .divide(avgP, 2, RoundingMode.HALF_UP);
            }
            out.add(new PriceAnalysisRow(
                    (String) r[0],
                    (String) r[1],
                    minP,
                    maxP,
                    avgP,
                    lineCount,
                    totalAmt,
                    volatilityPct));
        }
        return out;
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(o.toString());
    }

    public record MonthAmountRow(String month, BigDecimal amount) {}

    public record SupplierShareRow(
            String supplierCode,
            String supplierName,
            BigDecimal amount,
            BigDecimal sharePercent
    ) {}

    public record DeliveryAchievementRow(
            int completedOnTime,
            int completedLate,
            int openWithDueDate,
            BigDecimal onTimeRatePercent
    ) {}

    public record PriceAnalysisRow(
            String materialCode,
            String materialName,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            BigDecimal avgUnitPrice,
            long lineCount,
            BigDecimal totalAmount,
            BigDecimal volatilityPercent
    ) {}
}
