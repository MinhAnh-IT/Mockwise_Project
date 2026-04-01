package com.interview.judge.controller;

import com.interview.judge.judge0.Judge0CallbackPayload;
import com.interview.judge.service.JudgeOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/callback")
@Slf4j
@RequiredArgsConstructor
public class Judge0CallbackController {

    private final JudgeOrchestrator judgeOrchestrator;

    /**
     * Receives execution results from Judge0.
     *
     * <p>Judge0 POSTs to this endpoint once code execution finishes.
     * The {@code taskId} path variable identifies which {@code JudgeTaskResult}
     * this callback belongs to.
     *
     * @param taskId  UUID of the {@link com.interview.judge.entity.JudgeTaskResult}
     * @param payload Judge0 callback body (stdout/stderr are base64-encoded)
     * @return 200 OK on success, 500 on processing error
     */
    @RequestMapping(value = "/{taskId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Void> callback(
            @PathVariable UUID taskId,
            @RequestBody Judge0CallbackPayload payload) {

        log.info("Judge0 callback received: taskId={}, judge0Status={}",
                taskId, payload.getStatus() != null ? payload.getStatus().getId() : "null");

        try {
            judgeOrchestrator.handleCallback(taskId, payload);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Task not found in callback: taskId={}", taskId, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error handling Judge0 callback: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
