package com.mockwise.interview.service.admin;

import com.mockwise.interview.dto.admin.response.AdminSessionResponse;
import com.mockwise.interview.dto.admin.response.AdminSessionStatsResponse;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        return sessionRepo.findAll(spec, pageable).map(AdminSessionResponse::from);
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
