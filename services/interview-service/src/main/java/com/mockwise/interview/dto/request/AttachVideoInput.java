package com.mockwise.interview.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body for {@code POST /{sid}/answers/{aid}/attach-video} (realtime-stt-plan.md
 * §6.2.3). The FE calls this once the background video upload composes after a
 * Lever 2 fast-path submit, handing over the now-READY storage object so it can
 * be wired onto the already-scored answer and trigger background batch STT.
 */
public record AttachVideoInput(
        @NotNull(message = "storageObjectId is required")
        UUID storageObjectId
) {}
