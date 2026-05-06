package com.mockwise.questionbank.mapper;

import com.mockwise.questionbank.dto.request.CodingQuestionRequest;
import com.mockwise.questionbank.dto.response.CodingQuestionResponse;
import com.mockwise.questionbank.dto.response.QuestionSnapshotResponse;
import com.mockwise.questionbank.entity.CodingQuestion;
import com.mockwise.questionbank.entity.Question;
import org.mapstruct.*;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CodingMapper {

    // ── Request → Question (base fields only) ────────────────────────────────

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "type",        ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "createdBy",   ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    @Mapping(target = "askCount",    ignore = true)
    @Mapping(target = "lastAskedAt", ignore = true)
    @Mapping(target = "tags",        expression = "java(toArray(req.getTags()))")
    Question toQuestion(CodingQuestionRequest req);

    // ── Request → CodingQuestion (type-specific fields) ──────────────────────

    @Mapping(target = "id",       ignore = true)
    @Mapping(target = "question", ignore = true)
    CodingQuestion toEntity(CodingQuestionRequest req);

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
    void updateQuestion(@MappingTarget Question question, CodingQuestionRequest req);

    // ── Update CodingQuestion fields ──────────────────────────────────────────

    @Mapping(target = "id",       ignore = true)
    @Mapping(target = "question", ignore = true)
    void updateEntity(@MappingTarget CodingQuestion cq, CodingQuestionRequest req);

    // ── Entity → Response ────────────────────────────────────────────────────

    @Mapping(target = "id",                     source = "cq.question.id")
    @Mapping(target = "type",                   source = "cq.question.type")
    @Mapping(target = "difficulty",             source = "cq.question.difficulty")
    @Mapping(target = "status",                 source = "cq.question.status")
    @Mapping(target = "tags",                   expression = "java(toList(cq.getQuestion().getTags()))")
    @Mapping(target = "title",                  source = "cq.title")
    @Mapping(target = "description",            source = "cq.description")
    @Mapping(target = "timeLimitMinutes",       source = "cq.timeLimitMinutes")
    @Mapping(target = "optimalTimeComplexity",  source = "cq.optimalTimeComplexity")
    @Mapping(target = "optimalSpaceComplexity", source = "cq.optimalSpaceComplexity")
    @Mapping(target = "functionMeta",           source = "cq.functionMeta")
    @Mapping(target = "starterCode",            source = "cq.starterCode")
    @Mapping(target = "testCases",              source = "cq.testCases")
    @Mapping(target = "createdBy",              source = "cq.question.createdBy")
    @Mapping(target = "createdAt",              source = "cq.question.createdAt")
    @Mapping(target = "updatedAt",              source = "cq.question.updatedAt")
    CodingQuestionResponse toResponse(CodingQuestion cq);

    // ── Entity → Snapshot ────────────────────────────────────────────────────

    @Mapping(target = "snapshotAt",             expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "id",                     source = "cq.question.id")
    @Mapping(target = "type",                   source = "cq.question.type")
    @Mapping(target = "difficulty",             source = "cq.question.difficulty")
    @Mapping(target = "tags",                   expression = "java(toList(cq.getQuestion().getTags()))")
    @Mapping(target = "title",                  source = "cq.title")
    @Mapping(target = "description",            source = "cq.description")
    @Mapping(target = "timeLimitMinutes",        source = "cq.timeLimitMinutes")
    @Mapping(target = "optimalTimeComplexity",  source = "cq.optimalTimeComplexity")
    @Mapping(target = "optimalSpaceComplexity", source = "cq.optimalSpaceComplexity")
    @Mapping(target = "functionMeta",           source = "cq.functionMeta")
    @Mapping(target = "starterCode",            source = "cq.starterCode")
    @Mapping(target = "testCases",              source = "cq.testCases")
    // BEHAVIORAL fields → null
    @Mapping(target = "text",            ignore = true)
    @Mapping(target = "audioKey",        ignore = true)
    @Mapping(target = "competency",      ignore = true)
    @Mapping(target = "expectedSignals", ignore = true)
    // CORE_CONCEPTUAL fields → null
    @Mapping(target = "targetRoles",   ignore = true)
    @Mapping(target = "domain",        ignore = true)
    @Mapping(target = "keyConcepts",   ignore = true)
    @Mapping(target = "depthExpected", ignore = true)
    QuestionSnapshotResponse toSnapshot(CodingQuestion cq);

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
