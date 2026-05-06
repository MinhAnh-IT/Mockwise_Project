package com.mockwise.interview.service;

import com.mockwise.interview.dto.assessment.WeakTarget;
import com.mockwise.interview.client.ai.AiServiceAdapter;
import com.mockwise.interview.client.ai.dto.AiFollowUpRequest;
import com.mockwise.interview.client.ai.dto.AiFollowUpResponse;
import com.mockwise.interview.client.questionbank.QuestionBankAdapter;
import com.mockwise.interview.client.questionbank.dto.FollowUpResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionCandidate;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterRequest;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionSnapshotResponse;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.QuestionSource;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.TopicKind;
import com.mockwise.interview.repository.SessionQuestionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds {@link SessionQuestion} rows for a session. Three call sites:
 *
 * <ul>
 *   <li>{@link #pickForTopic} — first question of a fresh topic, called by
 *       SessionService at /start and by AnswerService when the planner
 *       decides MoveToNextTopic.</li>
 *   <li>{@link #pickFollowUpFromBank} — Tier 1 of §5.3: try to match a
 *       pre-authored follow-up by (kind, value).</li>
 *   <li>{@link #pickFollowUpFromAi} — Tier 2 fallback: ask the AI service
 *       to generate one. Persists with source=AI_GENERATED, no question_id.</li>
 * </ul>
 *
 * <p>Mark-asked is fired in {@link #pickForTopic} only — pre-authored
 * follow-ups belong to a different table in question-bank and don't share
 * the same counter; AI-generated ones aren't bank rows at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionPicker {

    QuestionBankAdapter questionBankAdapter;
    AiServiceAdapter aiServiceAdapter;
    SessionQuestionRepository sessionQuestionRepo;

    // ── Pick for a topic (first question or new topic after planner moves on) ─

    /**
     * Selection algorithm from question-selection-design.md §3 step 6:
     * pull a candidate pool with tag bias hints from the user profile,
     * then apply local scoring: opener bonus + tech / industry overlap
     * + low ask_count tie-breaker.
     *
     * <p>Relax sequence: if the strict pool is empty we drop
     * {@code requireOpener}, then widen difficulty by ±1, and finally
     * give up on {@code targetRole} too. Returning empty here is a hard
     * error — the orchestrator can't make progress without a question.
     */
    public SessionQuestion pickForTopic(
            UUID sessionId,
            BlueprintTopic topic,
            Difficulty difficulty,
            UserProfileResponse profile,
            List<String> excludeIds,
            int sequence
    ) {
        QuestionType type = type(topic);
        List<String> tagBias = buildTagBias(profile);

        QuestionCandidate chosen = tryFilter(topic, type, difficulty, profile, tagBias, excludeIds,
                /* requireOpener */ sequence == 1)
                .orElseGet(() -> tryFilter(topic, type, difficulty, profile, tagBias, excludeIds, false)
                        .orElseGet(() -> tryFilterRelaxed(topic, type, difficulty, tagBias, excludeIds)
                                .orElseThrow(() -> new BusinessException(
                                        StatusCode.QUESTION_BANK_UNAVAILABLE))));

        // Counter bump is fire-and-forget — see QuestionBankAdapter.markAskedSoft.
        questionBankAdapter.markAskedSoft(chosen.id());

        // Pull the rich snapshot from question-bank so the evaluator and the
        // end-of-session reviewer have expectedSignals/keyConcepts/depthExpected/
        // testCases without re-querying. Fallback to thin snapshot on outage.
        QuestionSnapshotResponse rich = questionBankAdapter.getSnapshotSoft(chosen.id()).orElse(null);

        return sessionQuestionRepo.save(SessionQuestion.builder()
                .sessionId(sessionId)
                .sequence(sequence)
                .questionId(chosen.id())
                .questionType(type)
                .topicKind(topic.getKind())
                .topicValue(topic.getTopicValue())
                .difficulty(difficulty)
                .isFollowUp(false)
                .source(QuestionSource.BANK)
                .snapshot(buildSnapshot(chosen, rich))
                .build());
    }

    /**
     * Two-stage scoring after the SQL filter does the hard pre-selection:
     *
     * <ol>
     *   <li>Opener bonus when this is the session's first question.</li>
     *   <li>+1 per tech-stack tag overlap, +1 per industry overlap.</li>
     *   <li>Low ask_count breaks ties — already enforced by question-bank's
     *       ORDER BY, so we just preserve the order Java-side.</li>
     * </ol>
     */
    private Optional<QuestionCandidate> tryFilter(
            BlueprintTopic topic, QuestionType type, Difficulty difficulty,
            UserProfileResponse profile, List<String> tagBias, List<String> excludeIds,
            boolean requireOpener) {

        QuestionFilterRequest req = new QuestionFilterRequest(
                type,
                topic.getKind() == TopicKind.COMPETENCY ? topic.getTopicValue() : null,
                topic.getKind() == TopicKind.DOMAIN     ? topic.getTopicValue() : null,
                profile != null && profile.position() != null
                        ? com.mockwise.interview.common.util.BlueprintNormalizer
                                .normalizeRole(profile.position().trackName())
                        : null,
                difficulty,
                tagBias,
                excludeIds,
                requireOpener,
                20);
        QuestionFilterResponse res = questionBankAdapter.filter(req);
        if (res == null || res.candidates() == null || res.candidates().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(applyLocalScoring(res.candidates(), profile, requireOpener));
    }

    /** Last-ditch relax: drop targetRole + tagBias, keep difficulty range ±1. */
    private Optional<QuestionCandidate> tryFilterRelaxed(
            BlueprintTopic topic, QuestionType type, Difficulty difficulty,
            List<String> tagBias, List<String> excludeIds) {

        for (Difficulty d : difficultyNeighbours(difficulty)) {
            QuestionFilterRequest req = new QuestionFilterRequest(
                    type,
                    topic.getKind() == TopicKind.COMPETENCY ? topic.getTopicValue() : null,
                    topic.getKind() == TopicKind.DOMAIN     ? topic.getTopicValue() : null,
                    null, // drop role
                    d,
                    null, // drop tag bias
                    excludeIds,
                    false,
                    20);
            QuestionFilterResponse res = questionBankAdapter.filter(req);
            if (res != null && res.candidates() != null && !res.candidates().isEmpty()) {
                return Optional.of(res.candidates().get(0));
            }
        }
        return Optional.empty();
    }

    private static List<Difficulty> difficultyNeighbours(Difficulty d) {
        return switch (d) {
            case EASY   -> List.of(Difficulty.EASY,   Difficulty.MEDIUM);
            case MEDIUM -> List.of(Difficulty.MEDIUM, Difficulty.EASY, Difficulty.HARD);
            case HARD   -> List.of(Difficulty.HARD,   Difficulty.MEDIUM);
        };
    }

    private static QuestionCandidate applyLocalScoring(
            List<QuestionCandidate> pool, UserProfileResponse profile, boolean requireOpener) {

        Set<String> tech = lowerSet(profile != null ? profile.techStack() : null);
        Set<String> industries = lowerSet(profile != null ? profile.industries() : null);

        return pool.stream()
                .max(Comparator.comparingInt(c -> {
                    int score = 0;
                    if (requireOpener && c.isOpener()) score += 10;
                    if (overlap(c.tags(), tech)) score += 3;
                    if (overlap(c.tags(), industries)) score += 3;
                    // ask_count is a tie-breaker: lighter penalty so it doesn't
                    // dominate the bonuses on cold pools.
                    score -= (int) (c.askCount() / 100);
                    return score;
                }))
                .orElseThrow();
    }

    // ── Pick for a follow-up ─────────────────────────────────────────────────

    /**
     * Tier 1 of §5.3: pre-authored follow-up matching (kind, value). Returns
     * empty when no match — caller falls back to the AI generator.
     */
    public Optional<SessionQuestion> pickFollowUpFromBank(
            UUID sessionId, SessionQuestion parent, WeakTarget weak,
            Difficulty difficulty, int sequence) {

        if (parent.getQuestionId() == null) {
            // The parent itself was AI_GENERATED — there's no bank parent to
            // hang a pre-authored follow-up off. Skip straight to Tier 2.
            return Optional.empty();
        }
        List<FollowUpResponse> matches = questionBankAdapter.findFollowUps(
                parent.getQuestionId(),
                weak.kind().name().toLowerCase(Locale.ROOT),
                weak.value());
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        FollowUpResponse fu = matches.get(0);
        return Optional.of(sessionQuestionRepo.save(SessionQuestion.builder()
                .sessionId(sessionId)
                .sequence(sequence)
                .questionId(fu.id())
                .questionType(parent.getQuestionType())
                .topicKind(parent.getTopicKind())
                .topicValue(parent.getTopicValue())
                .difficulty(difficulty)
                .parentQuestionId(parent.getQuestionId())
                .parentSessionQuestionId(parent.getId())
                .isFollowUp(true)
                .source(QuestionSource.PRE_AUTHORED_FOLLOWUP)
                .snapshot(buildFollowUpSnapshot(fu, parent))
                .build()));
    }

    /**
     * Tier 2 of §5.3: synchronous AI generation. Lands as an
     * {@link QuestionSource#AI_GENERATED} row carrying its content
     * inline — never written back to question-bank, so the caller must
     * always check {@code questionId == null} when downloading audio /
     * looking up bank metadata for these.
     */
    public SessionQuestion pickFollowUpFromAi(
            UUID sessionId, SessionQuestion parent, WeakTarget weak, List<com.mockwise.interview.dto.assessment.StrongTarget> strongs,
            Difficulty difficulty, String parentText, String parentTranscript,
            String parentCompetency, String parentDomain,
            List<String> parentExpectedSignals, List<String> parentKeyConcepts,
            String language, int sequence) {

        AiFollowUpRequest req = new AiFollowUpRequest(
                sessionId.toString(),
                new AiFollowUpRequest.ParentQuestion(
                        parent.getQuestionId() != null ? parent.getQuestionId() : "ai-generated",
                        parent.getQuestionType().name(),
                        parentText,
                        parentCompetency,
                        parentDomain,
                        parentExpectedSignals,
                        parentKeyConcepts),
                parentTranscript != null ? parentTranscript : "",
                new AiFollowUpRequest.WeakTarget(
                        weak.kind().name().toLowerCase(Locale.ROOT),
                        weak.value(),
                        weak.severity().name().toLowerCase(Locale.ROOT)),
                strongs == null ? List.of() : strongs.stream()
                        .map(s -> new AiFollowUpRequest.StrongTarget(
                                s.kind().name().toLowerCase(Locale.ROOT),
                                s.value()))
                        .toList(),
                difficulty.name(),
                language != null ? language : "vi");
        AiFollowUpResponse res = aiServiceAdapter.generateFollowUp(req);

        return sessionQuestionRepo.save(SessionQuestion.builder()
                .sessionId(sessionId)
                .sequence(sequence)
                .questionId(null)
                .questionType(parent.getQuestionType())
                .topicKind(parent.getTopicKind())
                .topicValue(parent.getTopicValue())
                .difficulty(difficulty)
                .parentQuestionId(parent.getQuestionId())
                .parentSessionQuestionId(parent.getId())
                .isFollowUp(true)
                .source(QuestionSource.AI_GENERATED)
                .inlineText(res.questionText())
                .inlineExpectedPoints(res.expectedPoints() != null ? res.expectedPoints() : new ArrayList<>())
                .snapshot(buildAiSnapshot(res, parent))
                .build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static QuestionType type(BlueprintTopic topic) {
        return topic.getKind() == TopicKind.COMPETENCY
                ? QuestionType.BEHAVIORAL
                : QuestionType.CORE_CONCEPTUAL;
    }

    /** Tag bias = tech_stack ∪ industries (normalised lowercase). */
    private static List<String> buildTagBias(UserProfileResponse profile) {
        Set<String> bias = new HashSet<>();
        if (profile != null) {
            if (profile.techStack() != null)  profile.techStack().forEach(s -> addLower(bias, s));
            if (profile.industries() != null) profile.industries().forEach(s -> addLower(bias, s));
        }
        return new ArrayList<>(bias);
    }

    private static void addLower(Set<String> sink, String s) {
        if (s != null && !s.isBlank()) sink.add(s.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> lowerSet(List<String> in) {
        if (in == null) return Set.of();
        Set<String> out = new HashSet<>(in.size());
        for (String s : in) addLower(out, s);
        return out;
    }

    private static boolean overlap(List<String> tags, Set<String> bias) {
        if (tags == null || tags.isEmpty() || bias.isEmpty()) return false;
        for (String t : tags) {
            if (t != null && bias.contains(t.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /**
     * Frozen snapshot stored on {@code SessionQuestion.snapshot}. Prefer the
     * rich snapshot from question-bank ({@code expectedSignals},
     * {@code keyConcepts}, {@code depthExpected}, coding fields) — fall
     * back to the thin candidate fields when the snapshot fetch failed so
     * a transient outage never blocks pinning.
     */
    private static Map<String, Object> buildSnapshot(QuestionCandidate c, QuestionSnapshotResponse rich) {
        Map<String, Object> m = new HashMap<>();
        // Always-present base fields
        m.put("id", c.id());
        m.put("type", c.type() != null ? c.type().name() : (rich != null ? rich.type() : null));
        m.put("difficulty", c.difficulty() != null ? c.difficulty().name() : (rich != null ? rich.difficulty() : null));
        m.put("text", rich != null && rich.text() != null ? rich.text() : c.text());
        m.put("audioKey", rich != null && rich.audioKey() != null ? rich.audioKey() : c.audioKey());
        m.put("tags", rich != null && rich.tags() != null ? rich.tags() : c.tags());
        m.put("snapshotAt", rich != null && rich.snapshotAt() != null ? rich.snapshotAt().toString() : null);

        // BEHAVIORAL — competency from candidate (always set), expectedSignals only from rich
        m.put("competency", c.competency() != null ? c.competency() : (rich != null ? rich.competency() : null));
        if (rich != null && rich.expectedSignals() != null) {
            m.put("expectedSignals", rich.expectedSignals());
        }

        // CORE_CONCEPTUAL — domain from candidate, keyConcepts/depthExpected/targetRoles from rich
        m.put("domain", c.domain() != null ? c.domain() : (rich != null ? rich.domain() : null));
        if (rich != null) {
            if (rich.keyConcepts() != null) m.put("keyConcepts", rich.keyConcepts());
            if (rich.depthExpected() != null) m.put("depthExpected", rich.depthExpected());
            if (rich.targetRoles() != null) m.put("targetRoles", rich.targetRoles());
        }

        // LIVE_CODING — only present when type=LIVE_CODING
        if (rich != null) {
            if (rich.title() != null) m.put("title", rich.title());
            if (rich.description() != null) m.put("description", rich.description());
            if (rich.timeLimitMinutes() != null) m.put("timeLimitMinutes", rich.timeLimitMinutes());
            if (rich.optimalTimeComplexity() != null) m.put("optimalTimeComplexity", rich.optimalTimeComplexity());
            if (rich.optimalSpaceComplexity() != null) m.put("optimalSpaceComplexity", rich.optimalSpaceComplexity());
            if (rich.functionMeta() != null) m.put("functionMeta", rich.functionMeta());
            if (rich.starterCode() != null) m.put("starterCode", rich.starterCode());
            if (rich.testCases() != null) m.put("testCases", rich.testCases());
        }
        return m;
    }

    /**
     * Pre-authored follow-up snapshot inherits topic context from the parent
     * (competency, domain, keyConcepts, expectedSignals, depthExpected) so
     * AnswerService.applyTranscriptReady can build the evaluator payload
     * without distinguishing follow-ups from openers.
     */
    private static Map<String, Object> buildFollowUpSnapshot(FollowUpResponse fu, SessionQuestion parent) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", fu.id());
        m.put("text", fu.text());
        m.put("audioKey", fu.audioKey());
        m.put("expectedPoints", fu.expectedPoints());
        m.put("probesTargetKind", fu.probesTargetKind());
        m.put("probesTargetValue", fu.probesTargetValue());
        inheritFromParent(m, parent);
        return m;
    }

    private static Map<String, Object> buildAiSnapshot(AiFollowUpResponse res, SessionQuestion parent) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", res.questionText());
        m.put("expectedPoints", res.expectedPoints());
        m.put("rationale", res.rationale());
        m.put("source", res.source());
        m.put("modelMeta", res.modelMeta());
        inheritFromParent(m, parent);
        return m;
    }

    /**
     * Copy topic-context fields from parent's snapshot into the follow-up
     * snapshot. Skips any field already set on the follow-up (e.g. explicit
     * {@code expectedPoints} on a pre-authored follow-up wins over the
     * parent's {@code expectedSignals}).
     */
    private static void inheritFromParent(Map<String, Object> sink, SessionQuestion parent) {
        if (parent == null || parent.getSnapshot() == null) return;
        Map<String, Object> ps = parent.getSnapshot();
        for (String k : List.of("competency", "domain", "keyConcepts", "expectedSignals", "depthExpected", "targetRoles")) {
            if (!sink.containsKey(k) && ps.get(k) != null) {
                sink.put(k, ps.get(k));
            }
        }
    }
}
