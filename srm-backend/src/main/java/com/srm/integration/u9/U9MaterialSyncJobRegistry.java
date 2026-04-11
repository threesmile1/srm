package com.srm.integration.u9;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class U9MaterialSyncJobRegistry {

    private final Map<String, U9MaterialSyncJobStatus> jobs = new ConcurrentHashMap<>();

    public String createJob() {
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        jobs.put(id, new U9MaterialSyncJobStatus(
                id, U9MaterialSyncJobStatus.PENDING, null, null, now, null));
        return id;
    }

    public void markRunning(String jobId) {
        jobs.computeIfPresent(jobId, (k, cur) -> new U9MaterialSyncJobStatus(
                jobId, U9MaterialSyncJobStatus.RUNNING, null, null, cur.createdAtEpochMs(), null));
    }

    public void markSuccess(String jobId, U9MaterialSyncService.U9MaterialSyncResult result) {
        long now = System.currentTimeMillis();
        jobs.computeIfPresent(jobId, (k, cur) -> new U9MaterialSyncJobStatus(
                jobId, U9MaterialSyncJobStatus.SUCCESS, result, null, cur.createdAtEpochMs(), now));
    }

    public void markFailed(String jobId, String message) {
        long now = System.currentTimeMillis();
        jobs.computeIfPresent(jobId, (k, cur) -> new U9MaterialSyncJobStatus(
                jobId, U9MaterialSyncJobStatus.FAILED, null, message, cur.createdAtEpochMs(), now));
    }

    public Optional<U9MaterialSyncJobStatus> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }
}
