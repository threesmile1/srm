package com.srm.integration.u9;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class U9MaterialSyncJobRunner {

    private final U9MaterialSyncService u9MaterialSyncService;
    private final U9MaterialSyncJobRegistry jobRegistry;

    @Async
    public void runAsync(String jobId) {
        jobRegistry.markRunning(jobId);
        try {
            U9MaterialSyncService.U9MaterialSyncResult result = u9MaterialSyncService.fetchAndApply();
            jobRegistry.markSuccess(jobId, result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            jobRegistry.markFailed(jobId, msg);
        }
    }
}
