package com.mockwise.practice.service;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.userprofile.UserProfileClient;
import com.mockwise.practice.client.userprofile.dto.ProfileBrief;
import com.mockwise.practice.dto.response.CommunityResponse;
import com.mockwise.practice.dto.response.LeaderboardResponse;
import com.mockwise.practice.repository.PracticeSubmissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cross-user ranking + community insights for the leaderboard page. Ranking is
 * computed on read from {@code practice_submission} (difficulty-weighted score
 * over distinct solved problems); display names are enriched from
 * user-profile-service, degrading to a placeholder when it is unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaderboardService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int MAX_LIMIT = 100;
    private static final int TRENDING_LIMIT = 10;
    private static final int HARDEST_LIMIT = 10;
    private static final long HARDEST_MIN_SUBMISSIONS = 5;

    PracticeSubmissionRepository submissionRepo;
    UserProfileClient userProfile;

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(String userId, String window, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Object[]> ranked = submissionRepo.leaderboardRanked(sinceFor(window));
        long total = ranked.size();

        int myIndex = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (userId.equals(ranked.get(i)[0])) {
                myIndex = i;
                break;
            }
        }

        List<Object[]> top = ranked.subList(0, Math.min(cappedLimit, ranked.size()));

        // Enrich the displayed rows (+ the caller, if outside the page) with names.
        Set<String> ids = new LinkedHashSet<>();
        top.forEach(r -> ids.add((String) r[0]));
        if (myIndex >= 0) ids.add(userId);
        Map<String, String> names = fetchNames(ids);

        List<LeaderboardResponse.Entry> entries = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            entries.add(toEntry(top.get(i), i + 1, names));
        }

        LeaderboardResponse.Me me = null;
        if (myIndex >= 0) {
            Object[] r = ranked.get(myIndex);
            me = new LeaderboardResponse.Me(
                    myIndex + 1, num(r[1]), num(r[2]), topPercent(myIndex + 1, total));
        }

        return new LeaderboardResponse(normalizeWindow(window), total, entries, me);
    }

    @Transactional(readOnly = true)
    public CommunityResponse community() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        List<CommunityResponse.Trending> trending = submissionRepo.trendingProblems(weekAgo).stream()
                .limit(TRENDING_LIMIT)
                .map(r -> new CommunityResponse.Trending(
                        (String) r[0], (String) r[1], (String) r[2], num(r[3])))
                .toList();

        List<CommunityResponse.Hardest> hardest = submissionRepo.hardestProblems(HARDEST_MIN_SUBMISSIONS).stream()
                .limit(HARDEST_LIMIT)
                .map(r -> {
                    long t = num(r[3]);
                    long acc = num(r[4]);
                    return new CommunityResponse.Hardest(
                            (String) r[0], (String) r[1], (String) r[2],
                            t, acc, t == 0 ? 0d : (double) acc / t);
                })
                .toList();

        return new CommunityResponse(trending, hardest);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LeaderboardResponse.Entry toEntry(Object[] r, int rank, Map<String, String> names) {
        String id = (String) r[0];
        return new LeaderboardResponse.Entry(
                rank, id, names.get(id),
                num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]));
    }

    /** Best-effort name lookup — leaderboard still renders (placeholder names) if profile is down. */
    private Map<String, String> fetchNames(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            ApiResponse<List<ProfileBrief>> res = userProfile.getBriefs(List.copyOf(ids));
            if (res != null && res.isSuccess() && res.getData() != null) {
                return res.getData().stream()
                        .filter(b -> b.userId() != null && b.fullName() != null)
                        .collect(Collectors.toMap(ProfileBrief::userId, ProfileBrief::fullName, (a, b) -> a));
            }
        } catch (Exception ex) {
            log.warn("Leaderboard name enrichment failed: {}", ex.getMessage());
        }
        return Map.of();
    }

    private LocalDateTime sinceFor(String window) {
        return switch (normalizeWindow(window)) {
            case "WEEK" -> LocalDateTime.now().minusDays(7);
            case "MONTH" -> LocalDateTime.now().minusDays(30);
            default -> EPOCH;
        };
    }

    private static String normalizeWindow(String window) {
        if (window == null) return "ALL";
        String w = window.trim().toUpperCase();
        return (w.equals("WEEK") || w.equals("MONTH")) ? w : "ALL";
    }

    private static int topPercent(int rank, long total) {
        if (total <= 0) return 100;
        return (int) Math.max(1, Math.ceil((double) rank / total * 100));
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
