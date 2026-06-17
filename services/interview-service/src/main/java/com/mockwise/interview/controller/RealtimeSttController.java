package com.mockwise.interview.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.ttsstt.dto.RealtimeSttTokenResponse;
import com.mockwise.interview.common.security.CustomUserDetails;
import com.mockwise.interview.service.AnswerService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browser-facing relay for the Lever 2 realtime STT token. The FE calls this
 * before recording; we mint a single-use ElevenLabs token via tts-stt (which
 * holds the API key) and hand it back so the browser can stream audio straight
 * to the ElevenLabs WebSocket. POST because each call spends a fresh token.
 *
 * <p>Gated by the {@code interview.realtime-stt.enabled} kill-switch in
 * {@link AnswerService#mintRealtimeSttToken} — disabled → 409 so the FE falls
 * back to the legacy flow.
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RealtimeSttController {

    AnswerService answerService;

    @PostMapping("/realtime-stt/token")
    public ResponseEntity<ApiResponse<RealtimeSttTokenResponse>> token(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                answerService.mintRealtimeSttToken(user.getUserId())));
    }
}
