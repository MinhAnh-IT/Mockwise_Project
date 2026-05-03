package com.mockwise.questionbank.dto.response;

import java.util.List;

public record QuestionFilterResponse(
        List<QuestionCandidateResponse> candidates,
        int total
) {}
