package com.interview.judge.mapper;

import com.interview.judge.dto.JudgeResultEvent;
import com.interview.judge.dto.SubmissionEvent;
import com.interview.judge.dto.TaskResultDto;
import com.interview.judge.dto.TestCaseDto;
import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;
import com.interview.judge.entity.enums.JobStatus;
import com.interview.judge.entity.enums.TaskStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedSourcePolicy = ReportingPolicy.IGNORE,
        imports = {JobStatus.class, TaskStatus.class},
        uses = ExpectedOutputSerializer.class
)
public interface JudgeMapper {

    /**
     * Maps {@link SubmissionEvent} → {@link JudgeJob}.
     * <ul>
     *   <li>{@code status}     = {@link JobStatus#RUNNING}</li>
     *   <li>{@code totalCases} = number of test cases</li>
     *   <li>{@code doneCases}  = 0</li>
     * </ul>
     */
    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "verdict",    ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "status",     expression = "java(JobStatus.RUNNING)")
    @Mapping(target = "totalCases", expression = "java(event.getTestCases().size())")
    @Mapping(target = "doneCases",  constant = "0")
    JudgeJob toJudgeJob(SubmissionEvent event);

    /**
     * Maps ({@link JudgeJob}, {@link TestCaseDto}, index) → {@link JudgeTaskResult}.
     * {@code expectedOutput} is serialized to JSON by {@link ExpectedOutputSerializer}.
     */
    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "stdout",         ignore = true)
    @Mapping(target = "stderr",         ignore = true)
    @Mapping(target = "judge0Token",    ignore = true)
    @Mapping(target = "runtimeMs",      ignore = true)
    @Mapping(target = "memoryKb",       ignore = true)
    @Mapping(target = "createdAt",      ignore = true)
    @Mapping(target = "finishedAt",     ignore = true)
    @Mapping(target = "job",            source = "job")
    @Mapping(target = "testCaseId",     source = "tc.id")
    @Mapping(target = "orderIndex",     source = "orderIndex")
    @Mapping(target = "status",         expression = "java(TaskStatus.PENDING)")
    @Mapping(target = "expectedOutput", source = "tc.expectedOutput", qualifiedByName = "serializeExpectedOutput")
    JudgeTaskResult toJudgeTaskResult(JudgeJob job, TestCaseDto tc, int orderIndex);

    /**
     * Maps {@link JudgeTaskResult} → {@link TaskResultDto}.
     * {@code status} enum is converted to its name string.
     */
    @Mapping(target = "status", expression = "java(result.getStatus().name())")
    TaskResultDto toTaskResultDto(JudgeTaskResult result);

    /** Delegates each element to {@link #toTaskResultDto}. */
    List<TaskResultDto> toTaskResultDtos(List<JudgeTaskResult> results);

    /**
     * Assembles {@link JudgeResultEvent} from finalized job + all task results.
     */
    @Mapping(target = "submissionId", source = "job.submissionId")
    @Mapping(target = "origin",       source = "job.origin")
    @Mapping(target = "verdict",      source = "job.verdict")
    @Mapping(target = "results",      source = "results")
    JudgeResultEvent toJudgeResultEvent(JudgeJob job, List<JudgeTaskResult> results);
}
