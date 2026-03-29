package com.mockwise.iam.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfileDraftRequest(
        @NotBlank(message = "Full name must not be blank")
        String fullName,
        
        @NotBlank(message = "Track ID must not be blank")
        String trackId,
        
        @NotBlank(message = "Level ID must not be blank")
        String levelId,
        
        @NotBlank(message = "City must not be blank")
        String city,
        
        @NotNull(message = "Experience must not be null")
        @Min(value = 0, message = "Experience must be 0 or greater")
        Integer experience
) { }
