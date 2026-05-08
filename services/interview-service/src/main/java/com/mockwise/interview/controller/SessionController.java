package com.mockwise.interview.controller;

import com.core.apiresponse.pagination.PageResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.common.security.CustomUserDetails;
import com.mockwise.interview.dto.request.StartSessionInput;
import com.mockwise.interview.dto.response.SessionSummaryView;
import com.mockwise.interview.dto.response.SessionView;
import com.mockwise.interview.dto.response.StartSessionOutput;
import com.mockwise.interview.service.SessionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public session endpoints. Path prefix comes from the servlet
 * context-path ({@code /api/v1/interviews}); controller paths are
 * relative to that.
 *
 * <ul>
 *   <li>{@code POST /start} — create a session, return the first question.</li>
 *   <li>{@code GET  /} — list the caller's sessions (history page).</li>
 *   <li>{@code GET  /{sid}} — full session detail incl. topic progress + pinned questions.</li>
 *   <li>{@code POST /{sid}/finish} — user-stopped end-of-session.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionController {

    SessionService sessionService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<StartSessionOutput>> start(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody StartSessionInput input) {
        StartSessionOutput output = sessionService.start(user.getUserId(), input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(output));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SessionSummaryView>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SessionSummaryView> p = sessionService.listForUser(
                user.getUserId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(p.getContent(), p.getNumber(), p.getSize(),
                        p.getTotalElements(), p.getTotalPages())));
    }

    @GetMapping("/{sid}")
    public ResponseEntity<ApiResponse<SessionView>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.getForUser(sid, user.getUserId())));
    }

    @PostMapping("/{sid}/finish")
    public ResponseEntity<ApiResponse<Void>> finish(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid) {
        sessionService.finish(sid, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
