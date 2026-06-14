package com.interview.judge.admin.dto;

import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Full drill-down for one job: summary fields + per-test-case results. */
public record JudgeJobDetail(
        JudgeJobSummary job,
        String judge0Token,
        List<TaskResultView> results
) {
    public record TaskResultView(
            UUID testCaseId,
            int orderIndex,
            String status,
            String judge0Token,
            Integer runtimeMs,
            Integer memoryKb,
            String stdout,
            String stderr,
            OffsetDateTime finishedAt
    ) {
        static TaskResultView from(JudgeTaskResult t) {
            return new TaskResultView(
                    t.getTestCaseId(),
                    t.getOrderIndex(),
                    t.getStatus().name(),
                    t.getJudge0Token(),
                    t.getRuntimeMs(),
                    t.getMemoryKb(),
                    t.getStdout(),
                    t.getStderr(),
                    t.getFinishedAt() == null ? null : t.getFinishedAt().atOffset(ZoneOffset.UTC)
            );
        }
    }

    public static JudgeJobDetail from(JudgeJob job, List<JudgeTaskResult> tasks) {
        return new JudgeJobDetail(
                JudgeJobSummary.from(job),
                job.getJudge0Token(),
                tasks.stream().map(TaskResultView::from).toList()
        );
    }
}
