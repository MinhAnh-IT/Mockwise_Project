package com.mockwise.interview.client.ttsstt;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.ttsstt.dto.TranscriptResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TtsSttAdapter {

    TtsSttClient client;

    public TranscriptResponse getTranscript(String transcriptId) {
        return unwrap(client.getTranscript(transcriptId));
    }

    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new BusinessException(StatusCode.UPSTREAM_RESPONSE_UNPARSABLE);
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    response.getCode(),
                    response.getMessage() != null ? response.getMessage() : "tts-stt call failed",
                    StatusCode.UPSTREAM_RESPONSE_UNPARSABLE.getHttpStatus());
        }
        return response.getData();
    }
}
