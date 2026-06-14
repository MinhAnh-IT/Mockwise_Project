package com.mockwise.iam.audit;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Derives the semantic fields ({@code action}, {@code category},
 * {@code targetType}, {@code targetId}) of an audit record from a raw HTTP
 * method + path. Centralising this here keeps the reactive gateway dumb — it
 * ships raw facts and this single, unit-testable class interprets them.
 *
 * <p>Convention: the resource segment immediately after {@code admin} names the
 * target ({@code .../admin/users/{id}/block} → target {@code USER}). A trailing
 * keyword like {@code block} / {@code status} / {@code default} refines the verb
 * ({@code USER_BLOCK}, {@code QUESTION_STATUS}, {@code BLUEPRINT_SET_DEFAULT}).
 */
public final class AuditClassifier {

    private AuditClassifier() {}

    public record Classification(String action, String category, String targetType, String targetId) {}

    /** UUID or all-digit segment counts as an entity id; subtype words (coding, behavioral) do not. */
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\d+)$");

    /** Trailing path keyword → action verb suffix. */
    private static final Map<String, String> MODIFIERS = Map.of(
            "block", "BLOCK",
            "unblock", "UNBLOCK",
            "status", "STATUS",
            "default", "SET_DEFAULT",
            "audio", "AUDIO_UPDATE",
            "regenerate", "AUDIO_REGENERATE");

    public static Classification classify(String httpMethod, String path) {
        if (path == null) {
            return new Classification("ADMIN_" + methodVerb(httpMethod), "CONTENT", "UNKNOWN", null);
        }

        List<String> parts = List.of(path.split("/")).stream().filter(s -> !s.isBlank()).toList();
        int adminIdx = parts.indexOf("admin");

        // Not an /admin/ path (shouldn't normally reach here) — record generically.
        if (adminIdx < 0 || adminIdx + 1 >= parts.size()) {
            return new Classification("ADMIN_" + methodVerb(httpMethod), "CONTENT", "UNKNOWN", null);
        }

        String resource = parts.get(adminIdx + 1);
        List<String> rest = parts.subList(adminIdx + 2, parts.size());

        String targetId = rest.stream().filter(s -> ID_SEGMENT.matcher(s).matches()).findFirst().orElse(null);

        // Last matching modifier wins so /audio/regenerate beats /audio.
        String verb = null;
        for (String seg : rest) {
            String mapped = MODIFIERS.get(seg.toLowerCase());
            if (mapped != null) verb = mapped;
        }
        if (verb == null) verb = methodVerb(httpMethod);

        String targetType = normalizeTarget(resource);
        String category = "USER".equals(targetType) ? "USER" : "CONTENT";

        return new Classification(targetType + "_" + verb, category, targetType, targetId);
    }

    private static String normalizeTarget(String resource) {
        String t = resource.toUpperCase().replace('-', '_');
        // Strip a simple trailing plural: USERS→USER, QUESTIONS→QUESTION, POSITION_TRACKS→POSITION_TRACK.
        if (t.endsWith("S") && t.length() > 1) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String methodVerb(String httpMethod) {
        if (httpMethod == null) return "ACCESS";
        return switch (httpMethod.toUpperCase()) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "ACCESS";
        };
    }

    /** HTTP status → coarse outcome. 2xx/3xx is success; everything else (and unknown) is failure. */
    public static String outcomeOf(Integer statusCode) {
        if (statusCode == null) return "SUCCESS";
        return (statusCode >= 200 && statusCode < 400) ? "SUCCESS" : "FAILURE";
    }
}
