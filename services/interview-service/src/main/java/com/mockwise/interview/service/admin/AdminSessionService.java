package com.mockwise.interview.service.admin;

import com.mockwise.interview.dto.admin.response.AdminSessionResponse;
import com.mockwise.interview.dto.admin.response.AdminSessionStatsResponse;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewBlueprintRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only admin oversight of interview sessions: a filtered/paginated list
 * and aggregate stats. There is intentionally NO get-by-id and NO mutation —
 * admins oversee and report, they do not inspect a session's contents or edit
 * it. The /admin/** gateway gate enforces ROLE_ADMIN.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminSessionService {

    InterviewSessionRepository sessionRepo;
    InterviewBlueprintRepository blueprintRepo;
    AnswerRepository answerRepo;

    @Transactional(readOnly = true)
    public Page<AdminSessionResponse> list(
            String userId,
            SessionStatus status,
            InterviewType interviewType,
            String targetRole,
            String level,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {

        Specification<InterviewSession> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (userId != null && !userId.isBlank()) {
                ps.add(cb.equal(root.get("userId"), userId.trim()));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (interviewType != null) {
                ps.add(cb.equal(root.get("interviewType"), interviewType));
            }
            if (targetRole != null && !targetRole.isBlank()) {
                ps.add(cb.equal(cb.upper(root.get("targetRole")), targetRole.trim().toUpperCase()));
            }
            if (level != null && !level.isBlank()) {
                ps.add(cb.equal(cb.upper(root.get("level")), level.trim().toUpperCase()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(Predicate[]::new));
        };

        Page<InterviewSession> page = sessionRepo.findAll(spec, pageable);

        // Per-session answer counts (A) for the page, batched into one grouped
        // query (no N+1).
        List<UUID> ids = page.getContent().stream().map(InterviewSession::getId).toList();
        Map<UUID, Long> answeredById = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : answerRepo.countGroupedBySessionId(ids)) {
                answeredById.put((UUID) row[0], toLong(row[1]));
            }
        }

        // Adaptive blueprint budget (B) for the page, read from the LIVE blueprint
        // — not session.question_count, which is a /start snapshot that drifts when
        // an admin later re-sizes the blueprint (the "2/2 behavioral" bug). Only
        // BEHAVIORAL/CORE need it; CODING's question_count is the fixed plan size
        // (topic slots, != questionBudget), so it stays the snapshot. One batched
        // lookup; a session whose blueprint was deleted falls back to the snapshot.
        Map<UUID, Integer> budgetByBlueprintId = new HashMap<>();
        List<UUID> blueprintIds = page.getContent().stream()
                .filter(s -> s.getInterviewType() != InterviewType.CODING)
                .map(InterviewSession::getBlueprintId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!blueprintIds.isEmpty()) {
            for (InterviewBlueprint b : blueprintRepo.findAllById(blueprintIds)) {
                budgetByBlueprintId.put(b.getId(), b.getQuestionBudget());
            }
        }

        return page.map(s -> {
            int answered = (int) toLong(answeredById.get(s.getId()));
            // B = total questions of the blueprint. CODING is a fixed plan, so its
            // snapshot question_count is authoritative; BEHAVIORAL/CORE read the
            // live budget (snapshot as fallback when the blueprint was deleted).
            Integer liveBudget = s.getInterviewType() == InterviewType.CODING || s.getBlueprintId() == null
                    ? null : budgetByBlueprintId.get(s.getBlueprintId());
            int budget = liveBudget != null ? liveBudget : s.getQuestionCount();
            // Display denominator = max(A, B): under budget the candidate stopped
            // early so anchor on the blueprint total (A/B); once answers reach or
            // pass the budget (follow-ups) the budget is no longer the ceiling, so
            // show A/A. CODING never has follow-ups, so A <= B always (A/B).
            int total = Math.max(answered, budget);
            return AdminSessionResponse.from(s, answered, total);
        });
    }

    @Transactional(readOnly = true)
    public AdminSessionStatsResponse stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SessionStatus s : SessionStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        long total = 0L;
        for (Object[] row : sessionRepo.countGroupedByStatus()) {
            String key = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = toLong(row[1]);
            byStatus.merge(key, count, Long::sum);
            total += count;
        }

        Map<String, Long> byType = new LinkedHashMap<>();
        for (InterviewType t : InterviewType.values()) {
            byType.put(t.name(), 0L);
        }
        for (Object[] row : sessionRepo.countGroupedByType()) {
            String key = row[0] != null ? row[0].toString() : "UNKNOWN";
            byType.merge(key, toLong(row[1]), Long::sum);
        }

        Double avg = sessionRepo.averageFinalScore();

        return new AdminSessionStatsResponse(total, byStatus, byType, avg);
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
