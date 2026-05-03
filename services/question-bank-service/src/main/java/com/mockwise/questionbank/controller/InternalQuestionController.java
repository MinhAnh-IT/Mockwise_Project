package com.mockwise.questionbank.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.questionbank.dto.response.MarkAskedResponse;
import com.mockwise.questionbank.service.QuestionSelectionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service endpoints. Currently called by interview-service after
 * pinning a question to a session — increments {@code ask_count} so future
 * selections can prefer cold questions.
 *
 * <p>Note: question-bank's SecurityConfig is currently permitAll, so the
 * /internal prefix is a path convention for now (gateway is expected to
 * strip the prefix from public traffic). Tighten when X-Internal-Auth is
 * wired into this service.
 */
@Slf4j
@RestController
@RequestMapping("/internal/questions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalQuestionController {

    QuestionSelectionService selectionService;

    @PostMapping("/{id}/mark-asked")
    public ResponseEntity<ApiResponse<MarkAskedResponse>> markAsked(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(selectionService.markAsked(id)));
    }
}
