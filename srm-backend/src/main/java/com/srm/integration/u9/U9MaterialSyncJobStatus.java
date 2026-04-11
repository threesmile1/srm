package com.srm.integration.u9;

/**
 * 异步 U9 物料同步任务状态。
 */
public record U9MaterialSyncJobStatus(
        String jobId,
        String state,
        U9MaterialSyncService.U9MaterialSyncResult result,
        String errorMessage,
        long createdAtEpochMs,
        Long finishedAtEpochMs
) {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
