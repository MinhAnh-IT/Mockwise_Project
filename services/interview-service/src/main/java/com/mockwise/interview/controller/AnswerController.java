package com.mockwise.interview.controller;

import com.core.apiresponse.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.client.storage.StorageAdapter;
import com.mockwise.interview.common.security.CustomUserDetails;
import com.mockwise.interview.dto.request.AttachVideoInput;
import com.mockwise.interview.dto.request.SubmitAnswerInput;
import com.mockwise.interview.dto.response.AnswerView;
import com.mockwise.interview.dto.response.CodingProblemView;
import com.mockwise.interview.dto.response.SubmitAnswerOutput;
import com.mockwise.interview.enums.SessionStatus;
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
    StorageAdapter storageAdapter;
    ObjectMapper objectMapper;

    @PostMapping("/{sid}/questions/{sqid}/answers")
    public ResponseEntity<ApiResponse<SubmitAnswerOutput>> submit(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID sqid,
            @Valid @RequestBody SubmitAnswerInput input) {
        SubmitAnswerOutput output = answerService.submit(sid, sqid, input, user.getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(output));
    }

    /**
     * Attaches the background-uploaded video to a Lever 2 fast-path answer
     * (realtime-stt-plan.md §6.2.3). Called after the clip composes; returns
     * 204. Idempotent + intentionally usable after the session clock is up /
     * the session is COMPLETED, so a late upload is never dropped.
     */
    @PostMapping("/{sid}/answers/{aid}/attach-video")
    public ResponseEntity<Void> attachVideo(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID aid,
            @Valid @RequestBody AttachVideoInput input) {
        answerService.attachVideo(sid, aid, input.storageObjectId(), user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sid}/questions/{sqid}/coding")
    public ResponseEntity<ApiResponse<CodingProblemView>> coding(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID sqid) {
        return ResponseEntity.ok(ApiResponse.success(
                answerService.getCodingProblem(sid, sqid, user.getUserId())));
    }

    @GetMapping("/{sid}/answers/{aid}")
    public ResponseEntity<ApiResponse<AnswerView>> get(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID sid,
            @PathVariable UUID aid) {
        var pair = answerService.getForUser(sid, aid, user.getUserId());
        // Per-question score / feedback / verdict stay hidden until the session
        // reaches SCORED — the candidate only sees the consolidated overall
        // review at the end of the interview.
        boolean reveal = pair.sessionStatus() == SessionStatus.SCORED;
        AnswerView view = AnswerView.fromEntity(
                pair.answer(), pair.questionType(), reveal, objectMapper);
        if (reveal) {
            // Presigned MinIO URL for the report's per-question replay.
            // Skipping signing while not revealing keeps mid-flight polls
            // free of the storage adapter call AND avoids leaking the id.
            view = view.withSignedMedia(
                    id -> storageAdapter.signInterviewVideoUrl(id, user.getUserId()).orElse(null));
        }
        return ResponseEntity.ok(ApiResponse.success(view));
    }
}
