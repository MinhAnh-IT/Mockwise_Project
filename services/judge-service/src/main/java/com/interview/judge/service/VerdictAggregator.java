package com.interview.judge.service;

import com.interview.judge.entity.JudgeTaskResult;
import com.interview.judge.entity.enums.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class VerdictAggregator {

    /**
     * Determines the overall verdict from all task results.
     *
     * <p>Priority (highest to lowest):
     * CE → TLE → MLE → RE → WA → AC
     *
     * @param results all JudgeTaskResult records for a job, ordered by index
     * @return verdict string: "AC", "WA", "TLE", "MLE", "RE", or "CE"
     */
    public String aggregate(List<JudgeTaskResult> results) {
        if (results == null || results.isEmpty()) {
            log.warn("No results to aggregate — defaulting to AC");
            return "AC";
        }

        if (hasStatus(results, TaskStatus.CE)) {
            return "CE";
        }
        if (hasStatus(results, TaskStatus.TLE)) {
            return "TLE";
        }
        if (hasStatus(results, TaskStatus.MLE)) {
            return "MLE";
        }
        if (hasStatus(results, TaskStatus.RE)) {
            return "RE";
        }
        if (hasStatus(results, TaskStatus.WA)) {
            return "WA";
        }

        return "AC";
    }

    private boolean hasStatus(List<JudgeTaskResult> results, TaskStatus target) {
        return results.stream().anyMatch(r -> target == r.getStatus());
    }
}
