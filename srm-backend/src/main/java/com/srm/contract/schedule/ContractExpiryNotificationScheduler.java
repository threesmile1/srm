package com.srm.contract.schedule;

import com.srm.contract.domain.Contract;
import com.srm.contract.domain.ContractStatus;
import com.srm.contract.repo.ContractRepository;
import com.srm.notification.service.NotificationService;
import com.srm.notification.service.StaffNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按「到期日前 30 天 / 7 天 / 当日」各提醒一次（每日 08:00，时区见 {@code srm.business-timezone}，默认 Asia/Shanghai）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryNotificationScheduler {

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ContractRepository contractRepository;
    private final NotificationService notificationService;
    private final StaffNotificationService staffNotificationService;

    @Scheduled(cron = "0 0 8 * * *", zone = "${srm.business-timezone:Asia/Shanghai}")
    public void remindExpiringContracts() {
        LocalDate today = LocalDate.now();
        remindForOffset(today, 30, "合同即将到期（剩余约30天）");
        remindForOffset(today, 7, "合同即将到期（剩余约7天）");
        remindForOffset(today, 0, "合同今日到期");
    }

    private void remindForOffset(LocalDate today, int daysFromToday, String titlePrefix) {
        LocalDate endDate = today.plusDays(daysFromToday);
        List<Contract> list = contractRepository.findByStatusAndEndDate(ContractStatus.ACTIVE, endDate);
        for (Contract c : list) {
            if (c.getEndDate() == null) {
                continue;
            }
            String endStr = c.getEndDate().format(DF);
            String content = String.format(
                    "合同 %s（%s）到期日 %s，请关注续签或终止。",
                    c.getContractNo(), c.getTitle(), endStr);
            try {
                notificationService.send(
                        null,
                        c.getSupplier().getId(),
                        titlePrefix,
                        content,
                        "CONTRACT_EXPIRY_REMINDER",
                        "CONTRACT",
                        c.getId());
            } catch (Exception e) {
                log.warn("合同到期提醒（供应商）失败 contractId={}: {}", c.getId(), e.getMessage());
            }
            try {
                staffNotificationService.notifyProcurementOrgStakeholders(
                        c.getProcurementOrg().getId(),
                        titlePrefix,
                        content,
                        "CONTRACT_EXPIRY_REMINDER",
                        "CONTRACT",
                        c.getId());
            } catch (Exception e) {
                log.warn("合同到期提醒（内部）失败 contractId={}: {}", c.getId(), e.getMessage());
            }
        }
    }
}
