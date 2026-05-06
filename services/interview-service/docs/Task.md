# Interview Service — Roadmap & Status

## Hệ thống
Microservices Spring Boot + AI service Python (LangGraph + Gemini). Stack:
- api-gateway, iam-service, user-profile-service, question-bank-service
- storage-service (MinIO), tts-stt-service, judge-service, mail-service
- interview-service (orchestrator), AI service (per-answer + overall review)
- Kafka (transactional outbox), Postgres (Flyway), Redis

## Luồng end-to-end
1. FE login (iam) → JWT; FE → POST /api/v1/interviews/start
2. interview-service: load profile (Feign user-profile) → BlueprintLoader.findFor(role, level, interviewType) → seedTopicStates → pickFirstTopic → questionPicker.pickForTopic (Task D: snapshot rich question metadata) → trả PinnedQuestionView (signed audio URL)
3. FE record video → upload presigned (storage-service /uploads/videos)
4. FE → POST /{sid}/questions/{sqid}/answers { storageObjectId }
   - AnswerService.submit → verifyStorageObject (GET /internal/objects/{id}) → save answer PROCESSING → outbox stage answer-submitted
5. tts-stt consume → STT → publish transcript-ready
6. interview-service TranscriptConsumer → applyTranscriptReady → fetch transcript → outbox stage evaluation-requested
7. AI service evaluator graph (router → behavioral|conceptual|live_coding evaluator → output_validator) → publish evaluation-completed
8. interview-service EvaluationConsumer → applyEvaluationCompleted → set answer SCORED → NextQuestionPlanner.plan (Case A/B/C/D, follow-up, dynamic difficulty) → pin câu kế (Task D snapshot) hoặc EndSession (COMPLETED)
9. (Task C) Khi session COMPLETED + tất cả answer terminal → SessionFinalizerService stage session-evaluation-requested → AI overall_reviewer graph → publish session-evaluation-completed → interview-service flip SCORED + finalScore + metadata.overallReview → outbox INTERVIEW_SCORED cho mail-service

## Code paths chính
**interview-service** (`src/main/java/com/mockwise/interview/`):
- service/SessionService.java — start/finish/getForUser
- service/AnswerService.java — submit, applyTranscriptReady/Failed, applyEvaluationCompleted/Failed, planner orchestration
- service/SessionFinalizerService.java — (Task C) maybeRequestOverallReview, idempotent
- service/QuestionPicker.java — pickForTopic / pickFollowUpFromBank / pickFollowUpFromAi; (Task D) snapshot full question metadata vào SessionQuestion.snapshot ngay khi pin câu mới
- service/BlueprintLoader.java — match blueprint by (role, level, type)
- service/admin/BlueprintAdminService.java — (Task A) CRUD
- planner/NextQuestionPlanner.java — adaptive decision tree
- entity/InterviewBlueprint.java — template (topics JSONB, questionBudget, timeBudgetMinutes, ...)
- entity/InterviewSession.java — status CREATED→IN_PROGRESS→COMPLETED→SCORED
- entity/Answer.java — status SUBMITTED→PROCESSING→READY→EVALUATING→SCORED|FAILED
- entity/SessionQuestion.java — snapshot JSONB (text, audioKey, competency, expectedSignals, domain, keyConcepts, depthExpected, ...)
- controller/SessionController.java — /start, /{sid}, /{sid}/finish
- controller/AnswerController.java — /{sid}/questions/{sqid}/answers, /{sid}/answers/{aid}
- controller/AdminBlueprintController.java — (Task A)
- dto/response/AnswerView.java — (Task B) reveal score/feedback/verdict only when session SCORED
- dto/response/SessionView.java — overallReview field (Task C)
- message/consumer/{Transcript,Evaluation,SessionEvaluation}Consumer.java
- message/publisher/OutboxPoller.java
- message/constants/KafkaTopics.java
- client/storage/StorageAdapter.java + Feign StorageClient (uses GET /internal/objects/{id})
- client/questionbank/QuestionBankAdapter.java + Feign (Task D: getSnapshot)
- client/userprofile/UserProfileAdapter.java + Feign

**AI service** (`Final-Project/AI/`):
- evaluator/graph.py — per-answer (behavioral / core_conceptual / live_coding)
- overall_reviewer/graph.py — (Task C) per-session aggregate review
- messaging/{evaluation,session_review}_{consumer,producer}.py
- api.py — FastAPI lifespan starts both consumers
- config.py — Kafka topics + groups

**storage-service**: InternalStorageController.java — GET /internal/objects/{id} (đã có), POST /internal/download-url, POST /internal/question-audio
**question-bank-service**: QuestionController.java — GET /questions/{id}/snapshot trả QuestionSnapshotResponse rich (Task D dùng)
**api-gateway**: application.yml routes; AuthGatewayFilter (admin path = bất kỳ path chứa /admin/)

## Nhiệm vụ — Checklist

### Task A: Admin quản lý Interview Templates
- [x] A.1 Gateway: route /api/v1/interviews/** → interview-service (api-gateway/application.yml)
- [x] A.2 InterviewBlueprintRepository: clearOtherDefaults + JpaSpecificationExecutor
- [x] A.3 InterviewSessionRepository: existsByBlueprintId
- [x] A.4 DTOs: BlueprintCreate/Update/Topic/SetDefault Request + BlueprintAdminResponse
- [x] A.5 BlueprintAdminService: create/list/get/update/setDefault/delete (normalize role+level, default toggle transactional)
- [x] A.6 AdminBlueprintController: POST/GET/PUT/PATCH/DELETE /admin/blueprints
- [x] A.7 StatusCode: BLUEPRINT_DUPLICATE / BLUEPRINT_IN_USE / BLUEPRINT_NO_TOPICS
- [ ] A.8 Smoke: curl create → list → setDefault → delete (block khi có session ref) — chờ deploy VPS

### Task D: Snapshot đầy đủ metadata câu hỏi vào SessionQuestion
- [x] D.1 QuestionBankClient.getSnapshot (Feign GET /questions/{id}/snapshot) + DTO QuestionSnapshotResponse (interview-service mirror)
- [x] D.2 QuestionBankAdapter.getSnapshotSoft (try/catch fallback về thin snapshot khi 404/lỗi)
- [x] D.3 QuestionPicker.buildSnapshot rewrite — fetch rich snapshot, populate expectedSignals/keyConcepts/depthExpected/targetRoles/title/testCases/...
- [x] D.4 buildFollowUpSnapshot — inherit competency/domain/expectedSignals/keyConcepts/depthExpected/targetRoles từ parent snapshot
- [x] D.5 buildAiSnapshot — inherit cùng các field từ parent snapshot
- [ ] D.6 Smoke: psql kiểm tra session_question.snapshot có đầy đủ field theo question type — chờ deploy VPS

### Task B: Ẩn kết quả per-question đến khi session SCORED
- [x] B.1 AnswerView.fromEntity(answer, revealResults) — mask score/maxScore/feedback/verdict/rubricScores khi !reveal
- [x] B.2 AnswerService.getForUser → trả record AnswerWithSession (Answer, SessionStatus)
- [x] B.3 AnswerController.get → reveal = (sessionStatus == SCORED)
- [x] B.4 SessionView.overallReview field — populate khi SCORED qua extractOverallReview
- [x] B.5 InterviewFlowIT — đã assert qua entity repo, không cần đổi (DTO masking không ảnh hưởng)

### Task C: AI đánh giá tổng quan + finalize SCORED
- [x] C.1 KafkaTopics: SESSION_EVALUATION_REQUESTED/COMPLETED/FAILED
- [x] C.2 AI/config.py: 3 topic env + consumer group
- [x] C.3 interview-service application.yml: bind 3 topic env
- [x] C.4 Java events: SessionEvaluationCompletedEvent (+ OverallReview record) / FailedEvent
- [x] C.5 Pydantic: AI/models/session_review.py + session_events.py (CamelModel, score 0–10)
- [x] C.6 AI/overall_reviewer/: graph.py, state.py, nodes/{input_validator, overall_evaluator, output_validator}.py, prompts/overall_review.py
- [x] C.7 AI/messaging/: session_review_producer.py + session_review_consumer.py
- [x] C.8 AI/api.py: lifespan start/stop session consumer + /health check
- [x] C.9 SessionFinalizerService.maybeRequestOverallReview (SELECT FOR UPDATE + metadata.sessionEvalRequestedAt dedup)
- [x] C.10 Repos: InterviewSessionRepository.findByIdForUpdate + AnswerRepository.countBySessionIdAndStatusIn
- [x] C.11 Hook: applyEvaluationCompleted, applyEvaluationFailed/applyTranscriptFailed (qua applyFailureTerminalState), SessionService.finish
- [x] C.12 SessionEvaluationConsumer: onCompleted → applyOverallReview; onFailed → metadata.overallReviewError
- [x] C.13 SessionService.applyOverallReview: finalScore + metadata.overallReview + status SCORED + outbox INTERVIEW_SCORED
- [x] C.14 SessionView.overallReview populate khi SCORED (đã hoàn tất ở Task B.4 + extractOverallReview)
- [x] C.15 StatusCode: SESSION_NOT_COMPLETED / SESSION_REVIEW_FAILED
- [x] C.16 E2E test (InterviewFinalizeFlowIT): full loop blueprint → planner end → SESSION_EVAL_REQUESTED outbox → applyOverallReview → SCORED + INTERVIEW_SCORED outbox + AnswerView mask trước/sau + idempotency. Pass 2/2 với VPS DB qua SSH tunnel

## Lưu ý quan trọng
- Old "Gap 1" (storage GET /internal/objects/{id}) — ĐÃ CÓ tại storage-service InternalStorageController.java:56-59
- Per-question evaluation VẪN chạy (planner cần verdict cho follow-up + dynamic difficulty); chỉ ẩn ở DTO của candidate
- Session_question rows trước Task D có thin snapshot — không backfill, chỉ session mới có rich snapshot
- Idempotency: metadata.sessionEvalRequestedAt + EventDedupService (`processed_event` table)
- Race condition: SELECT FOR UPDATE trên interview_session khi maybeRequestOverallReview & applyOverallReview
- Outbox payload luôn dùng camelCase (Java side khớp với CamelModel ở AI side)
- Score: 0–10 normalized; aggregate weighted theo topic.importance (HIGH=1.5, MED=1.0, LOW=0.5); FAILED → 0
- AdminPath check: api-gateway tự enforce ROLE_ADMIN cho path chứa /admin/, không cần @PreAuthorize trong controller

## Quick reference
- Start session: `POST /api/v1/interviews/start { interviewType }`
- Submit answer: `POST /api/v1/interviews/{sid}/questions/{sqid}/answers { type, storageObjectId|code, language }`
- Get answer (status polling): `GET /api/v1/interviews/{sid}/answers/{aid}`
- Get session (overall review khi SCORED): `GET /api/v1/interviews/{sid}`
- Finish: `POST /api/v1/interviews/{sid}/finish`
- Admin templates: `/api/v1/interviews/admin/blueprints` (POST/GET/PUT/PATCH/DELETE)

## Implementation order
A.1 → A.2-A.8 → D.1-D.6 → B.1-B.5 → C.1-C.5 (schemas) → C.6-C.8 (AI graph) → C.9-C.11 (interview trigger) → C.12-C.14 (consumer + projection) → C.15-C.16 (verify).
