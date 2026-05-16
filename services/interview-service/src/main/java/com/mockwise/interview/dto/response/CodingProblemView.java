package com.mockwise.interview.dto.response;

import com.mockwise.interview.entity.SessionQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The LIVE_CODING problem as the in-flight FE workspace needs it. Built
 * straight from the frozen {@code session_question.snapshot} so it never
 * re-queries question-bank.
 *
 * <p>Privacy (Task.md): only the fields the workspace renders / needs to
 * run code are exposed. Hidden test cases are dropped — {@link #sampleTestCases}
 * carries {@code is_hidden == false} cases only (with their expected output,
 * since those are the visible "sample" cases the FE may run against the
 * judge directly).
 *
 * <p>Wire shape mirrors {@code frontend/src/types/coding.ts CodingProblemView}.
 * Note BE stores the function return type under the JSON key {@code return}
 * (question-bank {@code FunctionMeta}); we re-emit it as {@code returnType}.
 */
public record CodingProblemView(
        String sessionQuestionId,
        int sequence,
        String title,
        String description,
        Integer timeLimitMinutes,
        String optimalTimeComplexity,
        String optimalSpaceComplexity,
        FunctionMeta functionMeta,
        Map<String, Object> starterCode,
        List<SampleTestCase> sampleTestCases
) {

    public record Param(String name, String type) {}

    public record FunctionMeta(
            String fn,
            List<Param> params,
            String returnType,
            boolean orderMatters,
            boolean inPlace
    ) {}

    public record SampleTestCase(
            String id,
            Map<String, Object> inputData,
            Map<String, Object> expectedOutput
    ) {}

    /**
     * Projects a pinned LIVE_CODING {@code SessionQuestion} into the FE
     * view. Caller is responsible for the type / ownership checks; this is
     * pure mapping over the snapshot blob.
     */
    @SuppressWarnings("unchecked")
    public static CodingProblemView fromSessionQuestion(SessionQuestion sq) {
        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();

        FunctionMeta fnMeta = null;
        if (snap.get("functionMeta") instanceof Map<?, ?> fm) {
            Map<String, Object> m = (Map<String, Object>) fm;
            List<Param> params = new ArrayList<>();
            if (m.get("params") instanceof List<?> ps) {
                for (Object p : ps) {
                    if (p instanceof Map<?, ?> pm) {
                        params.add(new Param(
                                asString(((Map<String, Object>) pm).get("name")),
                                asString(((Map<String, Object>) pm).get("type"))));
                    }
                }
            }
            fnMeta = new FunctionMeta(
                    asString(m.get("fn")),
                    params,
                    // question-bank serialises the return type under "return"
                    asString(m.get("return") != null ? m.get("return") : m.get("returnType")),
                    Boolean.TRUE.equals(m.get("orderMatters")),
                    Boolean.TRUE.equals(m.get("inPlace")));
        }

        Map<String, Object> starter = snap.get("starterCode") instanceof Map<?, ?> sc
                ? (Map<String, Object>) sc
                : Map.of();

        List<SampleTestCase> samples = new ArrayList<>();
        if (snap.get("testCases") instanceof List<?> tcs) {
            for (Object tc : tcs) {
                if (!(tc instanceof Map<?, ?> tm)) continue;
                Map<String, Object> t = (Map<String, Object>) tm;
                // Drop hidden cases — never leave the backend.
                if (Boolean.TRUE.equals(t.get("is_hidden")) || Boolean.TRUE.equals(t.get("hidden"))) {
                    continue;
                }
                samples.add(new SampleTestCase(
                        asString(t.get("id")),
                        t.get("inputData") instanceof Map<?, ?> in ? (Map<String, Object>) in : Map.of(),
                        t.get("expectedOutput") instanceof Map<?, ?> eo ? (Map<String, Object>) eo : Map.of()));
            }
        }

        return new CodingProblemView(
                sq.getId().toString(),
                sq.getSequence(),
                asString(snap.get("title")),
                asString(snap.get("description")),
                snap.get("timeLimitMinutes") instanceof Number n ? n.intValue() : null,
                asString(snap.get("optimalTimeComplexity")),
                asString(snap.get("optimalSpaceComplexity")),
                fnMeta,
                starter,
                samples);
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
