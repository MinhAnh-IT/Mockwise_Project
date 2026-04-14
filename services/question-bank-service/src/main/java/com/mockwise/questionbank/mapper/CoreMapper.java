package com.mockwise.questionbank.mapper;

import com.mockwise.questionbank.dto.request.CoreQuestionRequest;
import com.mockwise.questionbank.dto.response.CoreQuestionResponse;
import com.mockwise.questionbank.dto.response.QuestionSnapshotResponse;
import com.mockwise.questionbank.entity.CoreQuestion;
import com.mockwise.questionbank.entity.Question;
import com.mockwise.questionbank.enums.TargetRole;
import org.mapstruct.*;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CoreMapper {

    // ── Request → Question (base fields only) ─────────────────────────────��──

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "type",      ignore = true)
    @Mapping(target = "status",    ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tags",      expression = "java(toArray(req.getTags()))")
    Question toQuestion(CoreQuestionRequest req);

    // ── Request → CoreQuestion (type-specific fields) ────────────────────────

    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "question",      ignore = true)
    @Mapping(target = "audioKey",      ignore = true)
    @Mapping(target = "targetRoles",   expression = "java(roleListToArray(req.getTargetRoles()))")
    @Mapping(target = "domain",        source = "req.domain")
    @Mapping(target = "keyConcepts",   expression = "java(toArray(req.getKeyConcepts()))")
    CoreQuestion toEntity(CoreQuestionRequest req);

    // ── Update base Question fields ───────────��──────────────────────────────���

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "type",      ignore = true)
    @Mapping(target = "status",    ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tags",      expression = "java(toArray(req.getTags()))")
    void updateQuestion(@MappingTarget Question question, CoreQuestionRequest req);

    // ── Update CoreQuestion fields ────────────────────────────────────────────

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "question",    ignore = true)
    @Mapping(target = "audioKey",    ignore = true)
    @Mapping(target = "targetRoles", expression = "java(roleListToArray(req.getTargetRoles()))")
    @Mapping(target = "domain",      source = "req.domain")
    @Mapping(target = "keyConcepts", expression = "java(toArray(req.getKeyConcepts()))")
    void updateEntity(@MappingTarget CoreQuestion cq, CoreQuestionRequest req);

    // ── Entity → Response ────────────────────────────────────────────────────

    @Mapping(target = "id",            source = "cq.question.id")
    @Mapping(target = "type",          source = "cq.question.type")
    @Mapping(target = "difficulty",    source = "cq.question.difficulty")
    @Mapping(target = "status",        source = "cq.question.status")
    @Mapping(target = "tags",          expression = "java(toList(cq.getQuestion().getTags()))")
    @Mapping(target = "text",          source = "cq.text")
    @Mapping(target = "targetRoles",   expression = "java(toList(cq.getTargetRoles()))")
    @Mapping(target = "domain",        source = "cq.domain")
    @Mapping(target = "keyConcepts",   expression = "java(toList(cq.getKeyConcepts()))")
    @Mapping(target = "depthExpected", source = "cq.depthExpected")
    @Mapping(target = "audioKey",      source = "cq.audioKey")
    @Mapping(target = "createdBy",     source = "cq.question.createdBy")
    @Mapping(target = "createdAt",     source = "cq.question.createdAt")
    @Mapping(target = "updatedAt",     source = "cq.question.updatedAt")
    CoreQuestionResponse toResponse(CoreQuestion cq);

    // ── Entity → Snapshot ────────────────────────────────────────────────────

    @Mapping(target = "snapshotAt",      expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "id",              source = "cq.question.id")
    @Mapping(target = "type",            source = "cq.question.type")
    @Mapping(target = "difficulty",      source = "cq.question.difficulty")
    @Mapping(target = "tags",            expression = "java(toList(cq.getQuestion().getTags()))")
    @Mapping(target = "text",            source = "cq.text")
    @Mapping(target = "audioKey",        source = "cq.audioKey")
    @Mapping(target = "targetRoles",     expression = "java(toList(cq.getTargetRoles()))")
    @Mapping(target = "domain",          source = "cq.domain")
    @Mapping(target = "keyConcepts",     expression = "java(toList(cq.getKeyConcepts()))")
    @Mapping(target = "depthExpected",   source = "cq.depthExpected")
    // BEHAVIORAL fields → null
    @Mapping(target = "competency",      ignore = true)
    @Mapping(target = "expectedSignals", ignore = true)
    // LIVE_CODING fields → null
    @Mapping(target = "title",                  ignore = true)
    @Mapping(target = "description",            ignore = true)
    @Mapping(target = "timeLimitMinutes",        ignore = true)
    @Mapping(target = "optimalTimeComplexity",  ignore = true)
    @Mapping(target = "optimalSpaceComplexity", ignore = true)
    @Mapping(target = "functionMeta",           ignore = true)
    @Mapping(target = "starterCode",            ignore = true)
    @Mapping(target = "testCases",              ignore = true)
    QuestionSnapshotResponse toSnapshot(CoreQuestion cq);

    // ── Helpers ───────────────────────────────────────────────────────────────

    default String[] toArray(List<String> list) {
        if (list == null) return new String[0];
        return list.toArray(String[]::new);
    }

    default String[] roleListToArray(List<TargetRole> list) {
        if (list == null) return new String[0];
        return list.stream().map(Enum::name).toArray(String[]::new);
    }

    default List<String> toList(String[] arr) {
        if (arr == null) return List.of();
        return Arrays.asList(arr);
    }
}
