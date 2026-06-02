package com.mockwise.interview.service.admin;

import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.common.util.BlueprintNormalizer;
import com.mockwise.interview.dto.admin.request.BlueprintCreateRequest;
import com.mockwise.interview.dto.admin.request.BlueprintTopicDto;
import com.mockwise.interview.dto.admin.request.BlueprintUpdateRequest;
import com.mockwise.interview.dto.admin.response.BlueprintAdminResponse;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.TopicKind;
import com.mockwise.interview.repository.InterviewBlueprintRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only blueprint catalog. The endpoints under /admin/** are gated by
 * the API gateway (ROLE_ADMIN); this service trusts that and focuses on
 * the partial-unique-default invariant and the FK-guarded delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BlueprintAdminService {

    InterviewBlueprintRepository blueprintRepo;
    InterviewSessionRepository sessionRepo;

    @Transactional
    public BlueprintAdminResponse create(BlueprintCreateRequest req) {
        validateTopics(req.topics(), req.interviewType());

        String role = BlueprintNormalizer.normalizeRole(req.targetRole());
        String level = BlueprintNormalizer.normalizeLevel(req.level());
        boolean wantDefault = Boolean.TRUE.equals(req.isDefault());

        InterviewBlueprint b = InterviewBlueprint.builder()
                .targetRole(role)
                .level(level)
                .interviewType(req.interviewType())
                .topics(toEntities(req.topics(), req.interviewType()))
                .questionBudget(req.questionBudget() != null ? req.questionBudget() : 8)
                .timeBudgetMinutes(req.timeBudgetMinutes() != null ? req.timeBudgetMinutes() : 45)
                .maxFollowUpsPerTopic(req.maxFollowUpsPerTopic() != null ? req.maxFollowUpsPerTopic() : 2)
                .maxFollowUpsPerSession(req.maxFollowUpsPerSession() != null ? req.maxFollowUpsPerSession() : 4)
                .useAiSelector(Boolean.TRUE.equals(req.useAiSelector()))
                .isDefault(false)
                .build();
        b = blueprintRepo.save(b);

        if (wantDefault) {
            promoteToDefault(b);
        }
        log.info("Admin created blueprint {} role={} level={} type={} default={}",
                b.getId(), role, level, req.interviewType(), wantDefault);
        return BlueprintAdminResponse.from(b);
    }

    @Transactional(readOnly = true)
    public Page<BlueprintAdminResponse> list(String targetRole, String level,
                                             InterviewType interviewType, Boolean isDefault,
                                             Pageable pageable) {
        Specification<InterviewBlueprint> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (targetRole != null && !targetRole.isBlank()) {
                ps.add(cb.equal(root.get("targetRole"), BlueprintNormalizer.normalizeRole(targetRole)));
            }
            if (level != null && !level.isBlank()) {
                ps.add(cb.equal(root.get("level"), BlueprintNormalizer.normalizeLevel(level)));
            }
            if (interviewType != null) {
                ps.add(cb.equal(root.get("interviewType"), interviewType));
            }
            if (isDefault != null) {
                ps.add(cb.equal(root.get("isDefault"), isDefault));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(Predicate[]::new));
        };
        return blueprintRepo.findAll(spec, pageable).map(BlueprintAdminResponse::from);
    }

    @Transactional(readOnly = true)
    public BlueprintAdminResponse get(UUID id) {
        return BlueprintAdminResponse.from(load(id));
    }

    @Transactional
    public BlueprintAdminResponse update(UUID id, BlueprintUpdateRequest req) {
        validateTopics(req.topics(), req.interviewType());
        InterviewBlueprint b = load(id);

        b.setTargetRole(BlueprintNormalizer.normalizeRole(req.targetRole()));
        b.setLevel(BlueprintNormalizer.normalizeLevel(req.level()));
        b.setInterviewType(req.interviewType());
        b.setTopics(toEntities(req.topics(), req.interviewType()));
        if (req.questionBudget() != null) b.setQuestionBudget(req.questionBudget());
        if (req.timeBudgetMinutes() != null) b.setTimeBudgetMinutes(req.timeBudgetMinutes());
        if (req.maxFollowUpsPerTopic() != null) b.setMaxFollowUpsPerTopic(req.maxFollowUpsPerTopic());
        if (req.maxFollowUpsPerSession() != null) b.setMaxFollowUpsPerSession(req.maxFollowUpsPerSession());
        if (req.useAiSelector() != null) b.setUseAiSelector(req.useAiSelector());

        boolean wantDefault = Boolean.TRUE.equals(req.isDefault());
        if (wantDefault && !b.isDefault()) {
            promoteToDefault(b);
        } else if (!wantDefault && b.isDefault()) {
            b.setDefault(false);
            blueprintRepo.save(b);
        } else {
            blueprintRepo.save(b);
        }
        return BlueprintAdminResponse.from(b);
    }

    @Transactional
    public BlueprintAdminResponse setDefault(UUID id, boolean wantDefault) {
        InterviewBlueprint b = load(id);
        if (wantDefault) {
            promoteToDefault(b);
        } else if (b.isDefault()) {
            b.setDefault(false);
            blueprintRepo.save(b);
        }
        return BlueprintAdminResponse.from(b);
    }

    @Transactional
    public void delete(UUID id) {
        InterviewBlueprint b = load(id);
        if (sessionRepo.existsByBlueprintId(b.getId())) {
            throw new BusinessException(StatusCode.BLUEPRINT_IN_USE);
        }
        blueprintRepo.delete(b);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InterviewBlueprint load(UUID id) {
        return blueprintRepo.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.BLUEPRINT_NOT_FOUND));
    }

    private void promoteToDefault(InterviewBlueprint b) {
        // Clear sibling defaults BEFORE flipping ours, in one update — keeps
        // the partial unique index satisfied for the entire transaction.
        blueprintRepo.clearOtherDefaults(b.getTargetRole(), b.getLevel(), b.getInterviewType(), b.getId());
        b.setDefault(true);
        try {
            blueprintRepo.save(b);
            blueprintRepo.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Defensive: should not happen given the explicit clear above.
            throw new BusinessException(StatusCode.BLUEPRINT_DUPLICATE);
        }
    }

    /**
     * Cross-field topic validation that depends on the interview type — the
     * piece bean-validation on {@link BlueprintTopicDto} cannot express:
     * <ul>
     *   <li>BEHAVIORAL → every topic is a COMPETENCY with a non-blank value;</li>
     *   <li>CORE → every topic is a DOMAIN with a non-blank value;</li>
     *   <li>CODING → kind/topicValue are ignored (slot carries only difficulty).</li>
     * </ul>
     * The kind defaults to the type's expected kind when omitted, but an
     * explicit mismatch (e.g. a DOMAIN topic on a BEHAVIORAL blueprint) is
     * rejected so an admin cannot silently author a blueprint that breaks at
     * {@code /start}.
     */
    private static void validateTopics(List<BlueprintTopicDto> topics, InterviewType type) {
        if (topics == null || topics.isEmpty()) {
            throw new BusinessException(StatusCode.BLUEPRINT_NO_TOPICS);
        }
        if (type == InterviewType.CODING) {
            return; // CODING slots only need targetDifficulty (validated by @NotNull on the DTO)
        }
        TopicKind expected = type == InterviewType.BEHAVIORAL ? TopicKind.COMPETENCY : TopicKind.DOMAIN;
        for (BlueprintTopicDto d : topics) {
            if (d.kind() != null && d.kind() != expected) {
                throw new BusinessException(StatusCode.BLUEPRINT_TOPIC_INVALID,
                        type + " topics must be " + expected + ", got " + d.kind());
            }
            if (d.topicValue() == null || d.topicValue().isBlank()) {
                throw new BusinessException(StatusCode.BLUEPRINT_TOPIC_INVALID,
                        "topicValue is required for " + type + " topics");
            }
        }
    }

    private static List<BlueprintTopic> toEntities(List<BlueprintTopicDto> dtos, InterviewType type) {
        boolean coding = type == InterviewType.CODING;
        TopicKind expected = type == InterviewType.BEHAVIORAL ? TopicKind.COMPETENCY : TopicKind.DOMAIN;
        List<BlueprintTopic> out = new ArrayList<>(dtos.size());
        for (BlueprintTopicDto d : dtos) {
            out.add(BlueprintTopic.builder()
                    // CODING: store a clean slot (kind/topicValue null) matching the V5 seed.
                    .kind(coding ? null : expected)
                    .topicValue(coding || d.topicValue() == null ? null : d.topicValue().trim().toUpperCase())
                    .importance(d.importance())
                    .targetDifficulty(d.targetDifficulty())
                    .orderHint(d.orderHint())
                    .build());
        }
        return out;
    }
}
