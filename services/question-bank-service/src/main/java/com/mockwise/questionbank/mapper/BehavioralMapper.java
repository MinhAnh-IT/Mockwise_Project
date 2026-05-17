package com.mockwise.questionbank.mapper;

import com.mockwise.questionbank.dto.request.BehavioralQuestionRequest;
import com.mockwise.questionbank.dto.response.BehavioralQuestionResponse;
import com.mockwise.questionbank.dto.response.QuestionSnapshotResponse;
import com.mockwise.questionbank.entity.BehavioralQuestion;
import com.mockwise.questionbank.entity.Question;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface BehavioralMapper {

    // ── Request → Question (base fields only) ────────────────────────────────

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "type",        ignore = true)   // set by service
    @Mapping(target = "status",      ignore = true)   // default DRAFT
    @Mapping(target = "createdBy",   ignore = true)   // set by service
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    @Mapping(target = "askCount",    ignore = true)   // managed by markAsked endpoint
    @Mapping(target = "lastAskedAt", ignore = true)
    @Mapping(target = "tags",        expression = "java(toArray(req.getTags()))")
    Question toQuestion(BehavioralQuestionRequest req);

    // ── Request → BehavioralQuestion (type-specific fields) ──────────────────

    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "question",        ignore = true)  // set by service
    @Mapping(target = "audioKey",        ignore = true)  // managed separately
    @Mapping(target = "isOpener",        ignore = true)  // admin-set, default false
    @Mapping(target = "competency",      source = "req.competency")
    @Mapping(target = "expectedSignals", expression = "java(toArray(req.getExpectedSignals()))")
    BehavioralQuestion toEntity(BehavioralQuestionRequest req);

    // ── Update base Question fields ───────────────────────────────────────────

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "type",        ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "createdBy",   ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    @Mapping(target = "askCount",    ignore = true)
    @Mapping(target = "lastAskedAt", ignore = true)
    @Mapping(target = "tags",        expression = "java(toArray(req.getTags()))")
    void updateQuestion(@MappingTarget Question question, BehavioralQuestionRequest req);

    // ── Update BehavioralQuestion fields ─────────────────────────────────────

    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "question",        ignore = true)
    @Mapping(target = "audioKey",        ignore = true)
    @Mapping(target = "isOpener",        ignore = true)
    @Mapping(target = "competency",      source = "req.competency")
    @Mapping(target = "expectedSignals", expression = "java(toArray(req.getExpectedSignals()))")
    void updateEntity(@MappingTarget BehavioralQuestion bq, BehavioralQuestionRequest req);

    // ── Entity → Response ────────────────────────────────────────────────────

    @Mapping(target = "id",              source = "bq.question.id")
    @Mapping(target = "type",            source = "bq.question.type")
    @Mapping(target = "difficulty",      source = "bq.question.difficulty")
    @Mapping(target = "status",          source = "bq.question.status")
    @Mapping(target = "tags",            expression = "java(toList(bq.getQuestion().getTags()))")
    @Mapping(target = "text",            source = "bq.text")
    @Mapping(target = "competency",      source = "bq.competency")
    @Mapping(target = "expectedSignals", expression = "java(toList(bq.getExpectedSignals()))")
    @Mapping(target = "audioKey",        source = "bq.audioKey")
    @Mapping(target = "createdBy",       source = "bq.question.createdBy")
    @Mapping(target = "createdAt",       source = "bq.question.createdAt")
    @Mapping(target = "updatedAt",       source = "bq.question.updatedAt")
    BehavioralQuestionResponse toResponse(BehavioralQuestion bq);

    // ── Entity → Snapshot ────────────────────────────────────────────────────

    @Mapping(target = "snapshotAt",      expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "id",              source = "bq.question.id")
    @Mapping(target = "type",            source = "bq.question.type")
    @Mapping(target = "difficulty",      source = "bq.question.difficulty")
    @Mapping(target = "tags",            expression = "java(toList(bq.getQuestion().getTags()))")
    @Mapping(target = "text",            source = "bq.text")
    @Mapping(target = "audioKey",        source = "bq.audioKey")
    @Mapping(target = "competency",      source = "bq.competency")
    @Mapping(target = "expectedSignals", expression = "java(toList(bq.getExpectedSignals()))")
    // CORE_CONCEPTUAL fields → null
    @Mapping(target = "targetRoles",     ignore = true)
    @Mapping(target = "domain",          ignore = true)
    @Mapping(target = "keyConcepts",     ignore = true)
    @Mapping(target = "depthExpected",   ignore = true)
    // LIVE_CODING fields → null
    @Mapping(target = "title",                  ignore = true)
    @Mapping(target = "description",            ignore = true)
    @Mapping(target = "constraints",            ignore = true)
    @Mapping(target = "optimalTimeComplexity",  ignore = true)
    @Mapping(target = "optimalSpaceComplexity", ignore = true)
    @Mapping(target = "functionMeta",           ignore = true)
    @Mapping(target = "starterCode",            ignore = true)
    @Mapping(target = "testCases",              ignore = true)
    QuestionSnapshotResponse toSnapshot(BehavioralQuestion bq);

    // ── Helpers ───────────────────────────────────────────────────────────────

    default String[] toArray(List<String> list) {
        if (list == null) return new String[0];
        return list.toArray(String[]::new);
    }

    default List<String> toList(String[] arr) {
        if (arr == null) return List.of();
        return Arrays.asList(arr);
    }
}
