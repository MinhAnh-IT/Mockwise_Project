package com.mockwise.interview.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.common.security.CustomUserDetails;
import com.mockwise.interview.dto.request.SubmitAnswerInput;
import com.mockwise.interview.dto.response.AnswerView;
import com.mockwise.interview.dto.response.SubmitAnswerOutput;
import com.mockwise.interview.service.AnswerService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public answer endpoints. Path prefix comes from the servlet context-path.
 *
 * <ul>
 *   <li>{@code POST /{sid}/questions/{sqid}/answers} — submit a video or
 *       code answer for a pinned question. {@code sqid} here is the
 *       {@code session_question.id} (not the bank question id) so a
 *       follow-up answer is unambiguous.</li>
 *   <li>{@code GET  /{sid}/answers/{aid}} — poll endpoint for the FE.
 *       Returns the verdict + feedback once the row reaches SCORED.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnswerController {

    AnswerService answerService;

    @PostMapping("/{sid}/questions/{sqid}/answers")
    public ResponseEntity<ApiResponse<SubmitAnswerOutput>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID sqid,
            @Valid @RequestBody SubmitAnswerInput input) {
        SubmitAnswerOutput output = answerService.submit(sid, sqid, input, user.getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(output));
    }

    @GetMapping("/{sid}/answers/{aid}")
    public ResponseEntity<ApiResponse<AnswerView>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID aid) {
        var answer = answerService.getForUser(sid, aid, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(AnswerView.fromEntity(answer)));
    }
}
