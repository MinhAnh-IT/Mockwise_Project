package com.mockwise.iam.audit;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.audit.dto.AuditLogResponse;
import com.mockwise.iam.audit.dto.PagedResponse;
import com.mockwise.iam.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditService {

    static final int MAX_PAGE_SIZE = 100;

    AuditLogRepository repository;

    public PagedResponse<AuditLogResponse> search(String actorId, String action, String category,
                                                  String targetType, String targetId, String outcome,
                                                  String from, String to, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        return PagedResponse.of(
                repository.search(
                        blankToNull(actorId), blankToNull(action), blankToNull(category),
                        blankToNull(targetType), blankToNull(targetId), blankToNull(outcome),
                        parseInstant(from), parseInstant(to), pageable),
                AuditLogResponse::from);
    }

    public AuditLogResponse get(Long id) {
        return repository.findById(id)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND, "Audit record not found: " + id));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }

    /** Lenient ISO-8601 instant parse; a blank or malformed value disables the bound. */
    private static Instant parseInstant(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.BAD_REQUEST, "Invalid timestamp (expected ISO-8601): " + s);
        }
    }
}
