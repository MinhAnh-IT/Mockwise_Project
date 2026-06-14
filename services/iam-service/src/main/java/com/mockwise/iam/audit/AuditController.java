package com.mockwise.iam.audit;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.audit.dto.AuditLogResponse;
import com.mockwise.iam.audit.dto.PagedResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin audit-trail read API. Gated to ROLE_ADMIN by the gateway (path contains
 * {@code /admin/}). Read-only — audit rows are written solely by the Kafka
 * consumer and are never mutated here.
 */
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditController {

    AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.search(
                actorId, action, category, targetType, targetId, outcome, from, to, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(auditService.get(id)));
    }
}
