package com.interview.judge.e2e;

import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import com.interview.judge.dto.SubmissionEvent;
import com.interview.judge.dto.TestCaseDto;
import com.interview.judge.judge0.Judge0CallbackPayload;
import com.interview.judge.judge0.Judge0Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@EmbeddedKafka(partitions = 1, topics = {"submission-judged"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SubmissionE2ETest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    Judge0Client judge0Client;

    List<String> capturedCallbackUrls;

    @BeforeEach
    void setUp() {
        capturedCallbackUrls = new ArrayList<>();
        when(judge0Client.submitAsync(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    capturedCallbackUrls.add(inv.getArgument(2));
                    return UUID.randomUUID().toString();
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private UUID submit(SubmissionEvent event) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/judge/submit",
                HttpMethod.POST,
                new HttpEntity<>(event),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(response.getBody().get("submissionId").toString());
    }

    private void simulateCallback(String callbackUrl, int judge0StatusId, String stdout) {
        String taskId = callbackUrl.substring(callbackUrl.lastIndexOf('/') + 1);

        Judge0CallbackPayload payload = Judge0CallbackPayload.builder()
                .status(new Judge0CallbackPayload.StatusInfo(judge0StatusId, describeStatus(judge0StatusId)))
                .stdout(stdout != null ? base64(stdout) : null)
                .time("0.150")
                .memory(15000)
                .build();

        ResponseEntity<Void> r = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/judge/callback/" + taskId, payload, Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> pollUntilDone(UUID submissionId) {
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(
                        () -> fetchStatus(submissionId),
                        body -> body != null && "DONE".equals(body.get("status"))
                );
        return fetchStatus(submissionId);
    }

    private Map<String, Object> fetchStatus(UUID submissionId) {
        ResponseEntity<Map<String, Object>> r = restTemplate.exchange(
                "/api/v1/judge/status/" + submissionId,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
        );
        return r.getStatusCode() == HttpStatus.OK ? r.getBody() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResults(Map<String, Object> status) {
        return (List<Map<String, Object>>) status.get("results");
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    private static String describeStatus(int id) {
        return switch (id) {
            case 3 -> "Accepted";
            case 4 -> "Wrong Answer";
            case 5 -> "Time Limit Exceeded";
            case 6 -> "Compilation Error";
            default -> "Runtime Error";
        };
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void twoSum_allCorrect_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { return new int[]{0,1}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7, 11, 15), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build(),
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(3, 2, 4), "target", 6))
                                .expectedOutput(Map.of("result", List.of(1, 2))).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 2);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[0,1]");
        simulateCallback(capturedCallbackUrls.get(1), 3, "[1,2]");

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("AC");
        assertThat(getResults(status)).allMatch(r -> "AC".equals(r.get("status")));
        assertThat(status.get("doneCases")).isEqualTo(2);
        assertThat(status.get("totalCases")).isEqualTo(2);
    }

    @Test
    void twoSum_wrongOutput_verdictWA() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { return new int[]{0,0}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7, 11, 15), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[0,0]");

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("WA");
        assertThat(getResults(status).get(0).get("status")).isEqualTo("WA");
    }

    @Test
    void compilationError_verdictCE() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { invalid java }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(ParamMeta.builder().name("nums").type("int[]").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(1, 2)))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);

        String taskId = capturedCallbackUrls.get(0);
        taskId = taskId.substring(taskId.lastIndexOf('/') + 1);

        Judge0CallbackPayload payload = Judge0CallbackPayload.builder()
                .status(new Judge0CallbackPayload.StatusInfo(6, "Compilation Error"))
                .compileOutput(base64("Main.java:1: error: illegal start of expression"))
                .build();

        restTemplate.postForEntity("http://localhost:" + port + "/api/v1/judge/callback/" + taskId, payload, Void.class);

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("CE");
        assertThat(getResults(status).get(0).get("stderr"))
                .asString().contains("illegal start of expression");
    }

    @Test
    void runtimeError_verdictRE() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { throw new RuntimeException(); } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 11, null);

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("RE");
        assertThat(getResults(status).get(0).get("status")).isEqualTo("RE");
    }

    @Test
    void mixedResults_firstACSecondWA_verdictWA() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { return new int[]{0,1}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7, 11, 15), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build(),
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(3, 2, 4), "target", 6))
                                .expectedOutput(Map.of("result", List.of(1, 2))).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 2);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[0,1]");  // AC
        simulateCallback(capturedCallbackUrls.get(1), 3, "[0,1]");  // WA (wrong for case 2)

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("WA");
        assertThat(getResults(status).get(0).get("status")).isEqualTo("AC");
        assertThat(getResults(status).get(1).get("status")).isEqualTo("WA");
    }

    @Test
    void isValid_stringParam_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public boolean isValid(String s) { return true; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("isValid")
                        .params(List.of(ParamMeta.builder().name("s").type("String").build()))
                        .returnType("boolean").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("s", "()"))
                                .expectedOutput(Map.of("result", true)).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "true");

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    @Test
    void longestCommonPrefix_stringArrayParam_stringReturn_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public String longestCommonPrefix(String[] strs) { return \"fl\"; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("longestCommonPrefix")
                        .params(List.of(ParamMeta.builder().name("strs").type("String[]").build()))
                        .returnType("String").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("strs", List.of("flower", "flow", "flight")))
                                .expectedOutput(Map.of("result", "fl")).build()))
                .build();

        UUID submissionId = submit(event);

        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "fl");

        Map<String, Object> status = pollUntilDone(submissionId);

        assertThat(status.get("verdict")).isEqualTo("AC");
        assertThat(getResults(status).get(0).get("status")).isEqualTo("AC");
    }

    @Test
    void statusEndpoint_unknownSubmission_returns404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
                "/api/v1/judge/status/" + UUID.randomUUID(), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── ListNode ──────────────────────────────────────────────────────────

    @Test
    void reverseList_normalList_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public ListNode reverseList(ListNode head) { " +
                      "ListNode prev = null, curr = head; " +
                      "while (curr != null) { ListNode next = curr.next; curr.next = prev; prev = curr; curr = next; } " +
                      "return prev; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("reverseList")
                        .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                        .returnType("ListNode").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("head", List.of(1, 2, 3, 4, 5)))
                                .expectedOutput(Map.of("result", List.of(5, 4, 3, 2, 1))).build(),
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("head", List.of(1, 2)))
                                .expectedOutput(Map.of("result", List.of(2, 1))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 2);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[5,4,3,2,1]");
        simulateCallback(capturedCallbackUrls.get(1), 3, "[2,1]");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
        assertThat(getResults(status)).allMatch(r -> "AC".equals(r.get("status")));
    }

    @Test
    void reverseList_emptyList_verdictAC() {
        // This test validates the driver fix: null ListNode → "[]" not "null"
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public ListNode reverseList(ListNode head) { return null; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("reverseList")
                        .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                        .returnType("ListNode").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("head", List.of()))
                                .expectedOutput(Map.of("result", List.of())).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[]");  // driver now prints "[]" for null ListNode

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    @Test
    void reverseList_wrongOutput_verdictWA() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public ListNode reverseList(ListNode head) { return head; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("reverseList")
                        .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                        .returnType("ListNode").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("head", List.of(1, 2, 3)))
                                .expectedOutput(Map.of("result", List.of(3, 2, 1))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[1,2,3]");  // not reversed

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("WA");
    }

    // ── TreeNode ──────────────────────────────────────────────────────────

    @Test
    void maxDepth_normalTree_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int maxDepth(TreeNode root) { " +
                      "if (root == null) return 0; " +
                      "return 1 + Math.max(maxDepth(root.left), maxDepth(root.right)); } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("maxDepth")
                        .params(List.of(ParamMeta.builder().name("root").type("TreeNode").build()))
                        .returnType("int").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("root", Arrays.asList(3, 9, 20, null, null, 15, 7)))
                                .expectedOutput(Map.of("result", 3)).build(),
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("root", Arrays.asList(1, null, 2)))
                                .expectedOutput(Map.of("result", 2)).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 2);
        simulateCallback(capturedCallbackUrls.get(0), 3, "3");
        simulateCallback(capturedCallbackUrls.get(1), 3, "2");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    @Test
    void maxDepth_emptyTree_verdictAC() {
        // Validates driver fix: null TreeNode return → "[]"
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int maxDepth(TreeNode root) { return 0; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("maxDepth")
                        .params(List.of(ParamMeta.builder().name("root").type("TreeNode").build()))
                        .returnType("int").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("root", List.of()))
                                .expectedOutput(Map.of("result", 0)).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "0");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    // ── int[][] ───────────────────────────────────────────────────────────

    @Test
    void spiralOrder_matrix_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] spiralOrder(int[][] m) { return new int[]{1,2,3,6,9,8,7,4,5}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("spiralOrder")
                        .params(List.of(ParamMeta.builder().name("matrix").type("int[][]").build()))
                        .returnType("int[]").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("matrix", List.of(
                                        List.of(1, 2, 3),
                                        List.of(4, 5, 6),
                                        List.of(7, 8, 9))))
                                .expectedOutput(Map.of("result", List.of(1, 2, 3, 6, 9, 8, 7, 4, 5))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[1,2,3,6,9,8,7,4,5]");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    @Test
    void spiralOrder_wrongOutput_verdictWA() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] spiralOrder(int[][] m) { return new int[]{1,2,3}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("spiralOrder")
                        .params(List.of(ParamMeta.builder().name("matrix").type("int[][]").build()))
                        .returnType("int[]").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("matrix", List.of(List.of(1, 2, 3), List.of(4, 5, 6), List.of(7, 8, 9))))
                                .expectedOutput(Map.of("result", List.of(1, 2, 3, 6, 9, 8, 7, 4, 5))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[1,2,3]");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("WA");
    }

    // ── char[][] ─────────────────────────────────────────────────────────

    @Test
    void wordSearch_found_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public boolean exist(char[][] b, String w) { return true; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("exist")
                        .params(List.of(
                                ParamMeta.builder().name("board").type("char[][]").build(),
                                ParamMeta.builder().name("word").type("String").build()))
                        .returnType("boolean").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of(
                                        "board", List.of(
                                                List.of("A", "B", "C", "E"),
                                                List.of("S", "F", "C", "S"),
                                                List.of("A", "D", "E", "E")),
                                        "word", "ABCCED"))
                                .expectedOutput(Map.of("result", true)).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "true");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    @Test
    void wordSearch_notFound_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public boolean exist(char[][] b, String w) { return false; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("exist")
                        .params(List.of(
                                ParamMeta.builder().name("board").type("char[][]").build(),
                                ParamMeta.builder().name("word").type("String").build()))
                        .returnType("boolean").orderMatters(true).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of(
                                        "board", List.of(
                                                List.of("A", "B", "C", "E"),
                                                List.of("S", "F", "C", "S"),
                                                List.of("A", "D", "E", "E")),
                                        "word", "ABCB"))
                                .expectedOutput(Map.of("result", false)).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "false");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    // ── inPlace ───────────────────────────────────────────────────────────

    @Test
    void sortColors_inPlace_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public void sortColors(int[] nums) { java.util.Arrays.sort(nums); } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("sortColors")
                        .params(List.of(ParamMeta.builder().name("nums").type("int[]").build()))
                        .returnType("void").orderMatters(true).inPlace(true).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 0, 2, 1, 1, 0)))
                                .expectedOutput(Map.of("result", List.of(0, 0, 1, 1, 2, 2))).build(),
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 0, 1)))
                                .expectedOutput(Map.of("result", List.of(0, 1, 2))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 2);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[0,0,1,1,2,2]");
        simulateCallback(capturedCallbackUrls.get(1), 3, "[0,1,2]");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
        assertThat(getResults(status)).allMatch(r -> "AC".equals(r.get("status")));
    }

    @Test
    void sortColors_inPlace_wrongOutput_verdictWA() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public void sortColors(int[] nums) { /* do nothing */ } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("sortColors")
                        .params(List.of(ParamMeta.builder().name("nums").type("int[]").build()))
                        .returnType("void").orderMatters(true).inPlace(true).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 0, 1)))
                                .expectedOutput(Map.of("result", List.of(0, 1, 2))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 3, "[2,0,1]");  // unsorted

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("WA");
    }

    // ── orderMatters: false ───────────────────────────────────────────────

    @Test
    void twoSum_orderDoesNotMatter_differentIndexOrder_verdictAC() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { return new int[]{1,0}; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7, 11, 15), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        // stdout [1,0] must match expected [0,1] when orderMatters=false
        simulateCallback(capturedCallbackUrls.get(0), 3, "[1,0]");

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("AC");
    }

    // ── TLE ───────────────────────────────────────────────────────────────

    @Test
    void timeLimitExceeded_verdictTLE() {
        SubmissionEvent event = SubmissionEvent.builder()
                .submissionId(UUID.randomUUID())
                .language("java")
                .code("public class Solution { public int[] twoSum(int[] n, int t) { while(true){} return null; } }")
                .functionMeta(FunctionMeta.builder()
                        .fn("twoSum")
                        .params(List.of(
                                ParamMeta.builder().name("nums").type("int[]").build(),
                                ParamMeta.builder().name("target").type("int").build()))
                        .returnType("int[]").orderMatters(false).inPlace(false).build())
                .testCases(List.of(
                        TestCaseDto.builder().id(UUID.randomUUID())
                                .inputData(Map.of("nums", List.of(2, 7), "target", 9))
                                .expectedOutput(Map.of("result", List.of(0, 1))).build()))
                .build();

        UUID submissionId = submit(event);
        await().atMost(3, TimeUnit.SECONDS).until(() -> capturedCallbackUrls.size() == 1);
        simulateCallback(capturedCallbackUrls.get(0), 5, null);  // Judge0 status 5 = TLE

        Map<String, Object> status = pollUntilDone(submissionId);
        assertThat(status.get("verdict")).isEqualTo("TLE");
        assertThat(getResults(status).get(0).get("status")).isEqualTo("TLE");
    }
}
