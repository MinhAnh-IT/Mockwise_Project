package com.interview.judge.codebuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for {@code UniversalCppDriver.cpp} +
 * {@link CodeBuilder#generateCppDispatch}. Each test:
 *
 * <ol>
 *   <li>Loads the production driver template from {@code classpath:drivers/UniversalCppDriver.cpp}</li>
 *   <li>Calls {@link CodeBuilder#buildFullSource} to inject user code + dispatch</li>
 *   <li>Compiles the resulting source with {@code g++ -std=c++17 -O0}</li>
 *   <li>Pipes the Judge0 stdin protocol to the binary and asserts stdout</li>
 * </ol>
 *
 * <p><b>Note on environment.</b> The judge-service ships and runs on a VPS
 * where Judge0's sandbox uses GCC 9.2.0. These tests use whatever {@code g++}
 * is on the host machine (Apple clang on macOS dev, real GCC on Linux CI / VPS).
 * Both implement the C++17 standard library identically for the patterns the
 * driver exercises, so a green run on either is a strong signal that the
 * generated source is correct.
 *
 * <p>If {@code g++} is unavailable (e.g. minimal CI images), every test in
 * this class is skipped via {@link Assumptions} rather than failing the build.
 */
class CppDriverIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger TEST_COUNTER = new AtomicInteger();

    private static String CPP_DRIVER_CODE;
    private static boolean GPP_AVAILABLE;

    @TempDir
    Path tmpDir;

    @BeforeAll
    static void loadDriverAndProbeCompiler() throws IOException, InterruptedException {
        CPP_DRIVER_CODE = new String(
                CppDriverIntegrationTest.class.getResourceAsStream(
                        "/drivers/UniversalCppDriver.cpp").readAllBytes(),
                StandardCharsets.UTF_8);

        try {
            Process p = new ProcessBuilder("g++", "--version")
                    .redirectErrorStream(true)
                    .start();
            GPP_AVAILABLE = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            GPP_AVAILABLE = false;
        }
    }

    // ── Test harness ──────────────────────────────────────────────────────

    private CodeBuilder buildCodeBuilder() throws Exception {
        CodeBuilder cb = new CodeBuilder();
        // Inject the production C++ driver and stub the others (we only test C++ here).
        injectField(cb, "javaDriverCode", "");
        injectField(cb, "pythonDriverCode", "# === USER_CODE_INJECTED_HERE ===");
        injectField(cb, "jsDriverCode", "// === USER_CODE_INJECTED_HERE ===");
        injectField(cb, "cppDriverCode", CPP_DRIVER_CODE);
        return cb;
    }

    private static void injectField(CodeBuilder cb, String name, String value) throws Exception {
        var f = CodeBuilder.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(cb, value);
    }

    /** Compile + run + return stdout. Asserts exit code 0 and returns trimmed stdout. */
    private String runCpp(String userCode, FunctionMeta meta, List<String> paramLines) throws Exception {
        Assumptions.assumeTrue(GPP_AVAILABLE, "g++ unavailable — skipping C++ integration test");

        CodeBuilder cb = buildCodeBuilder();
        String fullSource = cb.buildFullSource(userCode, "cpp", meta);

        int id = TEST_COUNTER.incrementAndGet();
        Path src = tmpDir.resolve("judge_cpp_" + id + ".cpp");
        Path bin = tmpDir.resolve("judge_cpp_" + id);
        Files.writeString(src, fullSource, StandardCharsets.UTF_8);

        // Compile
        Process compile = new ProcessBuilder(
                "g++", "-std=c++17", "-O0",
                "-o", bin.toString(),
                src.toString())
                .redirectErrorStream(true)
                .start();
        boolean compiled = compile.waitFor(30, TimeUnit.SECONDS);
        if (!compiled) {
            compile.destroyForcibly();
            throw new AssertionError("g++ timed out compiling generated C++ source");
        }
        String compileOut = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (compile.exitValue() != 0) {
            throw new AssertionError("g++ failed (exit " + compile.exitValue() + "):\n" +
                    compileOut + "\n--- generated source ---\n" + fullSource);
        }

        // Run with stdin payload
        StringBuilder stdin = new StringBuilder();
        stdin.append(MAPPER.writeValueAsString(meta)).append("\n");
        for (String line : paramLines) stdin.append(line).append("\n");

        Process run = new ProcessBuilder(bin.toString())
                .redirectErrorStream(false)
                .start();
        try (OutputStream os = run.getOutputStream()) {
            os.write(stdin.toString().getBytes(StandardCharsets.UTF_8));
        }
        boolean done = run.waitFor(10, TimeUnit.SECONDS);
        if (!done) {
            run.destroyForcibly();
            throw new AssertionError("compiled binary timed out");
        }
        String stdout = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(run.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (run.exitValue() != 0) {
            throw new AssertionError("binary failed (exit " + run.exitValue() + "):\nSTDERR:\n" + stderr);
        }
        return stdout;
    }

    private static FunctionMeta meta(String fn, String returnType, boolean inPlace, ParamMeta... ps) {
        return FunctionMeta.builder()
                .fn(fn).returnType(returnType).inPlace(inPlace).orderMatters(true)
                .params(List.of(ps))
                .build();
    }
    private static ParamMeta p(String name, String type) {
        return ParamMeta.builder().name(name).type(type).build();
    }

    // ── Primitives ────────────────────────────────────────────────────────

    @Test
    void int_add() throws Exception {
        String code = "class Solution { public: int add(int a, int b) { return a + b; } };";
        String out = runCpp(code, meta("add", "int", false, p("a", "int"), p("b", "int")),
                List.of("3", "5"));
        assertThat(out).isEqualTo("8\n");
    }

    @Test
    void int_negative() throws Exception {
        String code = "class Solution { public: int neg(int n) { return -n; } };";
        String out = runCpp(code, meta("neg", "int", false, p("n", "int")), List.of("-7"));
        assertThat(out).isEqualTo("7\n");
    }

    @Test
    void int_max_int_boundary() throws Exception {
        String code = "class Solution { public: int echo(int n) { return n; } };";
        String out = runCpp(code, meta("echo", "int", false, p("n", "int")), List.of("2147483647"));
        assertThat(out).isEqualTo("2147483647\n");
    }

    @Test
    void long_above_32bit() throws Exception {
        String code = "class Solution { public: long long echo(long long n) { return n; } };";
        String out = runCpp(code, meta("echo", "long", false, p("n", "long")),
                List.of("9223372036854775807"));
        assertThat(out).isEqualTo("9223372036854775807\n");
    }

    @Test
    void double_passthrough() throws Exception {
        String code = "class Solution { public: double echo(double x) { return x; } };";
        String out = runCpp(code, meta("echo", "double", false, p("x", "double")),
                List.of("3.14"));
        assertThat(out).isEqualTo("3.14\n");
    }

    @Test
    void boolean_negate() throws Exception {
        String code = "class Solution { public: bool neg(bool b) { return !b; } };";
        String out = runCpp(code, meta("neg", "boolean", false, p("b", "boolean")),
                List.of("true"));
        assertThat(out).isEqualTo("false\n");
        out = runCpp(code, meta("neg", "boolean", false, p("b", "boolean")), List.of("false"));
        assertThat(out).isEqualTo("true\n");
    }

    @Test
    void boxed_aliases_normalize() throws Exception {
        String code = "class Solution { public: int mul(int a, int b) { return a * b; } };";
        String out = runCpp(code, meta("mul", "Integer", false,
                p("a", "Integer"), p("b", "Integer")), List.of("6", "7"));
        assertThat(out).isEqualTo("42\n");
    }

    // ── String / char ─────────────────────────────────────────────────────

    @Test
    void string_passthrough_with_spaces() throws Exception {
        String code = "class Solution { public: std::string echo(std::string s) { return s; } };";
        String out = runCpp(code, meta("echo", "String", false, p("s", "String")),
                List.of("hello world"));
        assertThat(out).isEqualTo("hello world\n");
    }

    @Test
    void string_empty() throws Exception {
        String code = "class Solution { public: std::string echo(std::string s) { return s; } };";
        String out = runCpp(code, meta("echo", "String", false, p("s", "String")),
                List.of(""));
        assertThat(out).isEqualTo("\n");
    }

    @Test
    void string_special_chars() throws Exception {
        String code = "class Solution { public: std::string echo(std::string s) { return s; } };";
        String out = runCpp(code, meta("echo", "String", false, p("s", "String")),
                List.of("a+b=c"));
        assertThat(out).isEqualTo("a+b=c\n");
    }

    @Test
    void string_to_boolean_isValid() throws Exception {
        String code =
                "class Solution {\n" +
                "public:\n" +
                "    bool isValid(std::string s) {\n" +
                "        std::stack<char> st;\n" +
                "        std::map<char,char> m{{')','('},{']','['},{'}','{'}};\n" +
                "        for (char c : s) {\n" +
                "            if (m.count(c)) { if (st.empty() || st.top() != m[c]) return false; st.pop(); }\n" +
                "            else st.push(c);\n" +
                "        }\n" +
                "        return st.empty();\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("isValid", "boolean", false, p("s", "String"));
        assertThat(runCpp(code, m, List.of("()[]{}"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("(]"))).isEqualTo("false\n");
        assertThat(runCpp(code, m, List.of(""))).isEqualTo("true\n");
    }

    @Test
    void char_input_and_return_uppercase() throws Exception {
        String code = "class Solution { public: char upper(char c) { return std::toupper(c); } };";
        String out = runCpp(code, meta("upper", "char", false, p("c", "char")),
                List.of("a"));
        assertThat(out).isEqualTo("A\n");
    }

    @Test
    void char_digit_passthrough() throws Exception {
        String code = "class Solution { public: char echo(char c) { return c; } };";
        String out = runCpp(code, meta("echo", "char", false, p("c", "char")), List.of("3"));
        assertThat(out).isEqualTo("3\n");
    }

    // ── 1-D arrays ────────────────────────────────────────────────────────

    @Test
    void int_array_reverse() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<int> reverse(std::vector<int>& a) {\n" +
                "        std::vector<int> r(a.rbegin(), a.rend()); return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("reverse", "int[]", false, p("a", "int[]")),
                List.of("[1,2,3]"));
        assertThat(out).isEqualTo("[3,2,1]\n");
    }

    @Test
    void int_array_empty() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<int> echo(std::vector<int>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[]", false, p("a", "int[]")),
                List.of("[]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void long_array_above_32bit() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<long long> echo(std::vector<long long>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "long[]", false, p("a", "long[]")),
                List.of("[9000000000,1]"));
        assertThat(out).isEqualTo("[9000000000,1]\n");
    }

    @Test
    void double_array_passthrough() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<double> echo(std::vector<double>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "double[]", false, p("a", "double[]")),
                List.of("[1.5,2.5]"));
        assertThat(out).isEqualTo("[1.5,2.5]\n");
    }

    @Test
    void string_array_passthrough() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::string> echo(std::vector<std::string>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "String[]", false, p("a", "String[]")),
                List.of("[\"a\",\"b\",\"c\"]"));
        assertThat(out).isEqualTo("[\"a\",\"b\",\"c\"]\n");
    }

    @Test
    void string_array_with_empty_string() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::string> echo(std::vector<std::string>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "String[]", false, p("a", "String[]")),
                List.of("[\"\",\"a\",\"b\"]"));
        assertThat(out).isEqualTo("[\"\",\"a\",\"b\"]\n");
    }

    @Test
    void twoSum_int_array_int_returns_int_array() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<int> twoSum(std::vector<int>& nums, int target) {\n" +
                "        std::unordered_map<int,int> seen;\n" +
                "        for (int i = 0; i < (int)nums.size(); i++) {\n" +
                "            int d = target - nums[i];\n" +
                "            if (seen.count(d)) return {seen[d], i};\n" +
                "            seen[nums[i]] = i;\n" +
                "        }\n" +
                "        return {};\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("twoSum", "int[]", false,
                        p("nums", "int[]"), p("target", "int")),
                List.of("[2,7,11,15]", "9"));
        assertThat(out).isEqualTo("[0,1]\n");
    }

    @Test
    void longestCommonPrefix() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::string longestCommonPrefix(std::vector<std::string>& strs) {\n" +
                "        if (strs.empty()) return \"\";\n" +
                "        std::string p = strs[0];\n" +
                "        for (size_t i = 1; i < strs.size(); i++) {\n" +
                "            while (strs[i].rfind(p, 0) != 0) { p.pop_back(); if (p.empty()) return \"\"; }\n" +
                "        }\n" +
                "        return p;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("longestCommonPrefix", "String", false, p("strs", "String[]"));
        assertThat(runCpp(code, m, List.of("[\"flower\",\"flow\",\"flight\"]"))).isEqualTo("fl\n");
        assertThat(runCpp(code, m, List.of("[\"dog\",\"racecar\",\"car\"]"))).isEqualTo("\n");
        assertThat(runCpp(code, m, List.of("[\"\"]"))).isEqualTo("\n");
    }

    // ── 2-D arrays ────────────────────────────────────────────────────────

    @Test
    void int_matrix_passthrough() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[][]", false, p("m", "int[][]")),
                List.of("[[1,2,3],[4,5,6]]"));
        assertThat(out).isEqualTo("[[1,2,3],[4,5,6]]\n");
    }

    @Test
    void int_matrix_empty() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[][]", false, p("m", "int[][]")),
                List.of("[]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void char_matrix_count_X() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int countX(std::vector<std::vector<char>>& b) {\n" +
                "        int n = 0; for (auto& r : b) for (char c : r) if (c == 'X') n++; return n;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("countX", "int", false, p("board", "char[][]")),
                List.of("[[\"X\",\"O\"],[\"X\",\"X\"]]"));
        assertThat(out).isEqualTo("3\n");
    }

    @Test
    void char_matrix_word_search_exist() throws Exception {
        String code =
                "class Solution {\n" +
                "    int R, C;\n" +
                "    bool dfs(std::vector<std::vector<char>>& b, std::string& w, int r, int c, int i) {\n" +
                "        if (i == (int)w.size()) return true;\n" +
                "        if (r < 0 || r >= R || c < 0 || c >= C || b[r][c] != w[i]) return false;\n" +
                "        char t = b[r][c]; b[r][c] = '#';\n" +
                "        bool ok = dfs(b,w,r+1,c,i+1)||dfs(b,w,r-1,c,i+1)||dfs(b,w,r,c+1,i+1)||dfs(b,w,r,c-1,i+1);\n" +
                "        b[r][c] = t; return ok;\n" +
                "    }\n" +
                "public:\n" +
                "    bool exist(std::vector<std::vector<char>>& board, std::string word) {\n" +
                "        R = board.size(); C = R ? board[0].size() : 0;\n" +
                "        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++)\n" +
                "            if (dfs(board, word, r, c, 0)) return true;\n" +
                "        return false;\n" +
                "    }\n" +
                "};";
        String board = "[[\"A\",\"B\",\"C\",\"E\"],[\"S\",\"F\",\"C\",\"S\"],[\"A\",\"D\",\"E\",\"E\"]]";
        FunctionMeta m = meta("exist", "boolean", false,
                p("board", "char[][]"), p("word", "String"));
        assertThat(runCpp(code, m, List.of(board, "ABCCED"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of(board, "ABCB"))).isEqualTo("false\n");
    }

    @Test
    void string_matrix_passthrough() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<std::string>> echo(std::vector<std::vector<std::string>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "String[][]", false, p("m", "String[][]")),
                List.of("[[\"eat\",\"tea\"],[\"bat\"]]"));
        assertThat(out).isEqualTo("[[\"eat\",\"tea\"],[\"bat\"]]\n");
    }

    // ── List<...> ─────────────────────────────────────────────────────────

    @Test
    void list_integer_double_each() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<int> doubleAll(std::vector<int>& xs) {\n" +
                "        std::vector<int> r; for (int x : xs) r.push_back(x*2); return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("doubleAll", "List<Integer>", false, p("xs", "List<Integer>")),
                List.of("[1,2,3]"));
        assertThat(out).isEqualTo("[2,4,6]\n");
    }

    @Test
    void list_string_upper() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::string> upper(std::vector<std::string>& ws) {\n" +
                "        for (auto& w : ws) for (auto& c : w) c = std::toupper(c); return ws;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("upper", "List<String>", false, p("ws", "List<String>")),
                List.of("[\"a\",\"bc\"]"));
        assertThat(out).isEqualTo("[\"A\",\"BC\"]\n");
    }

    @Test
    void list_list_integer_passthrough() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& x) { return x; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "List<List<Integer>>", false,
                        p("x", "List<List<Integer>>")),
                List.of("[[1,2],[3,4]]"));
        assertThat(out).isEqualTo("[[1,2],[3,4]]\n");
    }

    @Test
    void list_list_integer_threeSum() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::vector<int>> threeSum(std::vector<int>& nums) {\n" +
                "        std::sort(nums.begin(), nums.end());\n" +
                "        std::vector<std::vector<int>> res;\n" +
                "        int n = nums.size();\n" +
                "        for (int i = 0; i < n - 2; i++) {\n" +
                "            if (i > 0 && nums[i] == nums[i-1]) continue;\n" +
                "            int l = i+1, r = n-1;\n" +
                "            while (l < r) {\n" +
                "                int s = nums[i] + nums[l] + nums[r];\n" +
                "                if (s < 0) l++;\n" +
                "                else if (s > 0) r--;\n" +
                "                else {\n" +
                "                    res.push_back({nums[i], nums[l], nums[r]});\n" +
                "                    while (l < r && nums[l] == nums[l+1]) l++;\n" +
                "                    while (l < r && nums[r] == nums[r-1]) r--;\n" +
                "                    l++; r--;\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        return res;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("threeSum", "List<List<Integer>>", false,
                        p("nums", "int[]")),
                List.of("[-1,0,1,2,-1,-4]"));
        assertThat(out).isEqualTo("[[-1,-1,2],[-1,0,1]]\n");
    }

    @Test
    void list_list_string_groupAnagrams_presorted() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::vector<std::string>> groupAnagrams(std::vector<std::string>& strs) {\n" +
                "        std::map<std::string, std::vector<std::string>> m;\n" +
                "        for (auto& s : strs) { std::string k = s; std::sort(k.begin(), k.end()); m[k].push_back(s); }\n" +
                "        std::vector<std::vector<std::string>> res;\n" +
                "        for (auto& kv : m) { auto v = kv.second; std::sort(v.begin(), v.end()); res.push_back(v); }\n" +
                "        std::sort(res.begin(), res.end(), [](auto& a, auto& b){ return a[0] < b[0]; });\n" +
                "        return res;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("groupAnagrams", "List<List<String>>", false,
                        p("strs", "String[]")),
                List.of("[\"eat\",\"tea\",\"tan\",\"ate\",\"nat\",\"bat\"]"));
        assertThat(out).isEqualTo("[[\"ate\",\"eat\",\"tea\"],[\"bat\"],[\"nat\",\"tan\"]]\n");
    }

    // ── TreeNode ──────────────────────────────────────────────────────────

    @Test
    void tree_maxDepth_full_empty_skewed() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int maxDepth(TreeNode* root) {\n" +
                "        if (!root) return 0;\n" +
                "        return 1 + std::max(maxDepth(root->left), maxDepth(root->right));\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("maxDepth", "int", false, p("root", "TreeNode"));
        assertThat(runCpp(code, m, List.of("[3,9,20,null,null,15,7]"))).isEqualTo("3\n");
        assertThat(runCpp(code, m, List.of("[]"))).isEqualTo("0\n");
        assertThat(runCpp(code, m, List.of("[1,null,2]"))).isEqualTo("2\n");
    }

    @Test
    void tree_invertTree_returns_levelOrder() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    TreeNode* invertTree(TreeNode* root) {\n" +
                "        if (!root) return nullptr;\n" +
                "        TreeNode* t = root->left;\n" +
                "        root->left = invertTree(root->right);\n" +
                "        root->right = invertTree(t);\n" +
                "        return root;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("invertTree", "TreeNode", false, p("root", "TreeNode")),
                List.of("[4,2,7,1,3,6,9]"));
        assertThat(out).isEqualTo("[4,7,2,9,6,3,1]\n");
    }

    @Test
    void tree_null_return_serializes_as_empty() throws Exception {
        String code = "class Solution { public: TreeNode* f(TreeNode* root) { return nullptr; } };";
        String out = runCpp(code, meta("f", "TreeNode", false, p("root", "TreeNode")),
                List.of("[1]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void tree_construct_using_driver_struct() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    TreeNode* f(TreeNode* root) {\n" +
                "        TreeNode* r = new TreeNode(1);\n" +
                "        r->left = new TreeNode(2);\n" +
                "        r->right = new TreeNode(3);\n" +
                "        return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("f", "TreeNode", false, p("root", "TreeNode")),
                List.of("[]"));
        assertThat(out).isEqualTo("[1,2,3]\n");
    }

    // ── ListNode ──────────────────────────────────────────────────────────

    @Test
    void list_reverseList_full_and_empty() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* reverseList(ListNode* head) {\n" +
                "        ListNode* prev = nullptr;\n" +
                "        while (head) { ListNode* n = head->next; head->next = prev; prev = head; head = n; }\n" +
                "        return prev;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("reverseList", "ListNode", false, p("head", "ListNode"));
        assertThat(runCpp(code, m, List.of("[1,2,3,4,5]"))).isEqualTo("[5,4,3,2,1]\n");
        assertThat(runCpp(code, m, List.of("[]"))).isEqualTo("[]\n");
    }

    @Test
    void list_length_int_return() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int length(ListNode* head) {\n" +
                "        int n = 0; while (head) { n++; head = head->next; } return n;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("length", "int", false, p("head", "ListNode"));
        assertThat(runCpp(code, m, List.of("[10,20,30]"))).isEqualTo("3\n");
        assertThat(runCpp(code, m, List.of("[]"))).isEqualTo("0\n");
    }

    @Test
    void list_construct_using_driver_struct() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* doubleVals(ListNode* head) {\n" +
                "        ListNode dummy(0); ListNode* cur = &dummy;\n" +
                "        while (head) { cur->next = new ListNode(head->val * 2); cur = cur->next; head = head->next; }\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("doubleVals", "ListNode", false, p("head", "ListNode")),
                List.of("[1,2,3]"));
        assertThat(out).isEqualTo("[2,4,6]\n");
    }

    @Test
    void list_null_return_serializes_as_empty() throws Exception {
        String code = "class Solution { public: ListNode* empty(ListNode* head) { return nullptr; } };";
        String out = runCpp(code, meta("empty", "ListNode", false, p("head", "ListNode")),
                List.of("[1,2,3]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void list_with_negative_values() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* echo(ListNode* head) {\n" +
                "        ListNode dummy(0); ListNode* cur = &dummy;\n" +
                "        while (head) { cur->next = new ListNode(head->val); cur = cur->next; head = head->next; }\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("echo", "ListNode", false, p("head", "ListNode")),
                List.of("[-1,0,1]"));
        assertThat(out).isEqualTo("[-1,0,1]\n");
    }

    // ── inPlace ───────────────────────────────────────────────────────────

    @Test
    void inPlace_sortColors() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    void sortColors(std::vector<int>& nums) { std::sort(nums.begin(), nums.end()); }\n" +
                "};";
        String out = runCpp(code, meta("sortColors", "void", true, p("nums", "int[]")),
                List.of("[2,0,2,1,1,0]"));
        assertThat(out).isEqualTo("[0,0,1,1,2,2]\n");
    }

    @Test
    void inPlace_reverseString() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    void reverseString(std::vector<std::string>& s) { std::reverse(s.begin(), s.end()); }\n" +
                "};";
        String out = runCpp(code, meta("reverseString", "void", true, p("s", "String[]")),
                List.of("[\"h\",\"e\",\"l\",\"l\",\"o\"]"));
        assertThat(out).isEqualTo("[\"o\",\"l\",\"l\",\"e\",\"h\"]\n");
    }

    @Test
    void inPlace_moveZeroes() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    void moveZeroes(std::vector<int>& nums) {\n" +
                "        int j = 0;\n" +
                "        for (int i = 0; i < (int)nums.size(); i++)\n" +
                "            if (nums[i] != 0) std::swap(nums[i], nums[j++]);\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("moveZeroes", "void", true, p("nums", "int[]")),
                List.of("[0,1,0,3,12]"));
        assertThat(out).isEqualTo("[1,3,12,0,0]\n");
    }

    @Test
    void inPlace_rotateArray() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    void rotate(std::vector<int>& nums, int k) {\n" +
                "        int n = nums.size();\n" +
                "        if (n == 0) return;\n" +
                "        k %= n;\n" +
                "        std::reverse(nums.begin(), nums.end());\n" +
                "        std::reverse(nums.begin(), nums.begin() + k);\n" +
                "        std::reverse(nums.begin() + k, nums.end());\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("rotate", "void", true,
                        p("nums", "int[]"), p("k", "int")),
                List.of("[1,2,3,4,5,6,7]", "3"));
        assertThat(out).isEqualTo("[5,6,7,1,2,3,4]\n");
    }

    // ── Additional primitive coverage ─────────────────────────────────────

    @Test
    void int_zero() throws Exception {
        String code = "class Solution { public: int echo(int n) { return n; } };";
        String out = runCpp(code, meta("echo", "int", false, p("n", "int")), List.of("0"));
        assertThat(out).isEqualTo("0\n");
    }

    @Test
    void int_min_int_boundary() throws Exception {
        String code = "class Solution { public: int echo(int n) { return n; } };";
        String out = runCpp(code, meta("echo", "int", false, p("n", "int")),
                List.of("-2147483648"));
        assertThat(out).isEqualTo("-2147483648\n");
    }

    @Test
    void long_negative_boundary() throws Exception {
        String code = "class Solution { public: long long echo(long long n) { return n; } };";
        String out = runCpp(code, meta("echo", "long", false, p("n", "long")),
                List.of("-9223372036854775807"));
        assertThat(out).isEqualTo("-9223372036854775807\n");
    }

    @Test
    void mixed_primitive_args_int_long_returns_long() throws Exception {
        String code = "class Solution { public:\n" +
                "    long long combine(int a, long long b) { return (long long)a + b; }\n" +
                "};";
        String out = runCpp(code, meta("combine", "long", false,
                        p("a", "int"), p("b", "long")),
                List.of("100", "9000000000"));
        assertThat(out).isEqualTo("9000000100\n");
    }

    // ── Additional string / char coverage ─────────────────────────────────

    @Test
    void string_lowercase_alias_normalizes() throws Exception {
        String code = "class Solution { public: std::string echo(std::string s) { return s; } };";
        String out = runCpp(code, meta("echo", "string", false, p("s", "string")),
                List.of("ok"));
        assertThat(out).isEqualTo("ok\n");
    }

    @Test
    void character_alias_normalizes() throws Exception {
        String code = "class Solution { public: char echo(char c) { return c; } };";
        String out = runCpp(code, meta("echo", "Character", false, p("c", "Character")),
                List.of("X"));
        assertThat(out).isEqualTo("X\n");
    }

    @Test
    void isAnagram_two_strings_to_boolean() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    bool isAnagram(std::string s, std::string t) {\n" +
                "        if (s.size() != t.size()) return false;\n" +
                "        std::array<int, 26> cnt{};\n" +
                "        for (char c : s) cnt[c - 'a']++;\n" +
                "        for (char c : t) cnt[c - 'a']--;\n" +
                "        for (int v : cnt) if (v != 0) return false;\n" +
                "        return true;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("isAnagram", "boolean", false,
                p("s", "String"), p("t", "String"));
        assertThat(runCpp(code, m, List.of("anagram", "nagaram"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("rat", "car"))).isEqualTo("false\n");
        assertThat(runCpp(code, m, List.of("", ""))).isEqualTo("true\n");
    }

    @Test
    void reverseString_returns_string() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::string reverse(std::string s) { std::reverse(s.begin(), s.end()); return s; }\n" +
                "};";
        String out = runCpp(code, meta("reverse", "String", false, p("s", "String")),
                List.of("hello"));
        assertThat(out).isEqualTo("olleh\n");
    }

    // ── Additional 1-D array coverage ─────────────────────────────────────

    @Test
    void int_array_single_element() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<int> echo(std::vector<int>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[]", false, p("a", "int[]")),
                List.of("[42]"));
        assertThat(out).isEqualTo("[42]\n");
    }

    @Test
    void int_array_with_negatives_and_duplicates() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<int> echo(std::vector<int>& a) { return a; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[]", false, p("a", "int[]")),
                List.of("[-5,-3,1,1,2,2]"));
        assertThat(out).isEqualTo("[-5,-3,1,1,2,2]\n");
    }

    @Test
    void int_array_sum_returns_int() throws Exception {
        String code = "class Solution { public:\n" +
                "    int sum(std::vector<int>& a) { int s = 0; for (int v : a) s += v; return s; }\n" +
                "};";
        String out = runCpp(code, meta("sum", "int", false, p("a", "int[]")),
                List.of("[1,2,3,4,5]"));
        assertThat(out).isEqualTo("15\n");
    }

    @Test
    void findDuplicate_int_array_to_int() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int findDuplicate(std::vector<int>& nums) {\n" +
                "        std::unordered_set<int> seen;\n" +
                "        for (int n : nums) { if (seen.count(n)) return n; seen.insert(n); }\n" +
                "        return -1;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("findDuplicate", "int", false, p("nums", "int[]")),
                List.of("[1,3,4,2,2]"));
        assertThat(out).isEqualTo("2\n");
    }

    @Test
    void int_to_int_array_range() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<int> range(int n) {\n" +
                "        std::vector<int> r(n);\n" +
                "        for (int i = 0; i < n; i++) r[i] = i;\n" +
                "        return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("range", "int[]", false, p("n", "int")),
                List.of("5"));
        assertThat(out).isEqualTo("[0,1,2,3,4]\n");
    }

    @Test
    void string_array_concat_to_string() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::string join(std::vector<std::string>& ws) {\n" +
                "        std::string r;\n" +
                "        for (size_t i = 0; i < ws.size(); i++) {\n" +
                "            if (i > 0) r += \",\";\n" +
                "            r += ws[i];\n" +
                "        }\n" +
                "        return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("join", "String", false, p("ws", "String[]")),
                List.of("[\"a\",\"bc\",\"def\"]"));
        assertThat(out).isEqualTo("a,bc,def\n");
    }

    // ── Additional 2-D array coverage ─────────────────────────────────────

    @Test
    void int_matrix_1x1() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[][]", false, p("m", "int[][]")),
                List.of("[[42]]"));
        assertThat(out).isEqualTo("[[42]]\n");
    }

    @Test
    void int_matrix_single_row() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[][]", false, p("m", "int[][]")),
                List.of("[[1,2,3]]"));
        assertThat(out).isEqualTo("[[1,2,3]]\n");
    }

    @Test
    void int_matrix_single_column() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& m) { return m; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "int[][]", false, p("m", "int[][]")),
                List.of("[[1],[2],[3]]"));
        assertThat(out).isEqualTo("[[1],[2],[3]]\n");
    }

    @Test
    void int_matrix_transpose() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::vector<int>> transpose(std::vector<std::vector<int>>& m) {\n" +
                "        if (m.empty()) return {};\n" +
                "        int R = m.size(), C = m[0].size();\n" +
                "        std::vector<std::vector<int>> t(C, std::vector<int>(R));\n" +
                "        for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) t[c][r] = m[r][c];\n" +
                "        return t;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("transpose", "int[][]", false, p("m", "int[][]")),
                List.of("[[1,2,3],[4,5,6]]"));
        assertThat(out).isEqualTo("[[1,4],[2,5],[3,6]]\n");
    }

    @Test
    void int_matrix_sum_to_int() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int sumAll(std::vector<std::vector<int>>& m) {\n" +
                "        int s = 0; for (auto& r : m) for (int v : r) s += v; return s;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("sumAll", "int", false, p("m", "int[][]")),
                List.of("[[1,2],[3,4]]"));
        assertThat(out).isEqualTo("10\n");
    }

    @Test
    void char_matrix_to_char_matrix_uppercase() throws Exception {
        // char[][] → char[][] round-trip with mutation.
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::vector<char>> upper(std::vector<std::vector<char>>& b) {\n" +
                "        for (auto& r : b) for (auto& c : r) c = std::toupper(c);\n" +
                "        return b;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("upper", "char[][]", false, p("b", "char[][]")),
                List.of("[[\"a\",\"b\"],[\"c\",\"d\"]]"));
        assertThat(out).isEqualTo("[[\"A\",\"B\"],[\"C\",\"D\"]]\n");
    }

    // ── Additional List<...> coverage ─────────────────────────────────────

    @Test
    void list_integer_empty() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<int> echo(std::vector<int>& xs) { return xs; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "List<Integer>", false, p("xs", "List<Integer>")),
                List.of("[]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void list_list_integer_empty_outer() throws Exception {
        String code = "class Solution { public:\n" +
                "    std::vector<std::vector<int>> echo(std::vector<std::vector<int>>& x) { return x; }\n" +
                "};";
        String out = runCpp(code, meta("echo", "List<List<Integer>>", false,
                        p("x", "List<List<Integer>>")),
                List.of("[]"));
        assertThat(out).isEqualTo("[]\n");
    }

    @Test
    void list_string_filter_returns_list() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::string> filter(std::vector<std::string>& ws, std::string prefix) {\n" +
                "        std::vector<std::string> r;\n" +
                "        for (auto& w : ws) if (w.rfind(prefix, 0) == 0) r.push_back(w);\n" +
                "        return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("filter", "List<String>", false,
                        p("ws", "List<String>"), p("prefix", "String")),
                List.of("[\"eat\",\"egg\",\"bat\"]", "e"));
        assertThat(out).isEqualTo("[\"eat\",\"egg\"]\n");
    }

    @Test
    void list_integer_int_to_list_pascal_row() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<int> getRow(int rowIndex) {\n" +
                "        std::vector<int> row(rowIndex + 1, 1);\n" +
                "        for (int i = 1; i <= rowIndex; i++)\n" +
                "            for (int j = i - 1; j > 0; j--)\n" +
                "                row[j] += row[j - 1];\n" +
                "        return row;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("getRow", "List<Integer>", false, p("rowIndex", "int")),
                List.of("4"));
        assertThat(out).isEqualTo("[1,4,6,4,1]\n");
    }

    // ── Additional TreeNode coverage ──────────────────────────────────────

    @Test
    void tree_single_node_max_depth_is_one() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int maxDepth(TreeNode* root) {\n" +
                "        if (!root) return 0;\n" +
                "        return 1 + std::max(maxDepth(root->left), maxDepth(root->right));\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("maxDepth", "int", false, p("root", "TreeNode")),
                List.of("[5]"));
        assertThat(out).isEqualTo("1\n");
    }

    @Test
    void tree_isSymmetric_returns_boolean() throws Exception {
        String code =
                "class Solution {\n" +
                "    bool sym(TreeNode* a, TreeNode* b) {\n" +
                "        if (!a && !b) return true;\n" +
                "        if (!a || !b) return false;\n" +
                "        return a->val == b->val && sym(a->left, b->right) && sym(a->right, b->left);\n" +
                "    }\n" +
                "public:\n" +
                "    bool isSymmetric(TreeNode* root) { return !root || sym(root->left, root->right); }\n" +
                "};";
        FunctionMeta m = meta("isSymmetric", "boolean", false, p("root", "TreeNode"));
        assertThat(runCpp(code, m, List.of("[1,2,2,3,4,4,3]"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("[1,2,2,null,3,null,3]"))).isEqualTo("false\n");
        assertThat(runCpp(code, m, List.of("[]"))).isEqualTo("true\n");
    }

    @Test
    void tree_inorderTraversal_returns_list() throws Exception {
        String code =
                "class Solution {\n" +
                "    void in(TreeNode* n, std::vector<int>& r) {\n" +
                "        if (!n) return;\n" +
                "        in(n->left, r); r.push_back(n->val); in(n->right, r);\n" +
                "    }\n" +
                "public:\n" +
                "    std::vector<int> inorderTraversal(TreeNode* root) {\n" +
                "        std::vector<int> r; in(root, r); return r;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("inorderTraversal", "List<Integer>", false,
                        p("root", "TreeNode")),
                List.of("[1,null,2,3]"));
        assertThat(out).isEqualTo("[1,3,2]\n");
    }

    @Test
    void tree_levelOrder_returns_list_of_lists() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    std::vector<std::vector<int>> levelOrder(TreeNode* root) {\n" +
                "        std::vector<std::vector<int>> res;\n" +
                "        if (!root) return res;\n" +
                "        std::queue<TreeNode*> q; q.push(root);\n" +
                "        while (!q.empty()) {\n" +
                "            int sz = q.size();\n" +
                "            std::vector<int> level;\n" +
                "            for (int i = 0; i < sz; i++) {\n" +
                "                TreeNode* n = q.front(); q.pop();\n" +
                "                level.push_back(n->val);\n" +
                "                if (n->left) q.push(n->left);\n" +
                "                if (n->right) q.push(n->right);\n" +
                "            }\n" +
                "            res.push_back(level);\n" +
                "        }\n" +
                "        return res;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("levelOrder", "List<List<Integer>>", false,
                        p("root", "TreeNode")),
                List.of("[3,9,20,null,null,15,7]"));
        assertThat(out).isEqualTo("[[3],[9,20],[15,7]]\n");
    }

    @Test
    void tree_isValidBST_returns_boolean() throws Exception {
        String code =
                "class Solution {\n" +
                "    bool ok(TreeNode* n, long long lo, long long hi) {\n" +
                "        if (!n) return true;\n" +
                "        if (n->val <= lo || n->val >= hi) return false;\n" +
                "        return ok(n->left, lo, n->val) && ok(n->right, n->val, hi);\n" +
                "    }\n" +
                "public:\n" +
                "    bool isValidBST(TreeNode* root) {\n" +
                "        return ok(root, LLONG_MIN, LLONG_MAX);\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("isValidBST", "boolean", false, p("root", "TreeNode"));
        assertThat(runCpp(code, m, List.of("[2,1,3]"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("[5,1,4,null,null,3,6]"))).isEqualTo("false\n");
    }

    @Test
    void tree_negative_values() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int sumTree(TreeNode* root) {\n" +
                "        if (!root) return 0;\n" +
                "        return root->val + sumTree(root->left) + sumTree(root->right);\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("sumTree", "int", false, p("root", "TreeNode")),
                List.of("[-1,-2,-3,-4,null,null,-5]"));
        assertThat(out).isEqualTo("-15\n");
    }

    // ── Additional ListNode coverage ──────────────────────────────────────

    @Test
    void list_single_node_round_trip() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* echo(ListNode* head) {\n" +
                "        ListNode dummy(0); ListNode* cur = &dummy;\n" +
                "        while (head) { cur->next = new ListNode(head->val); cur = cur->next; head = head->next; }\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("echo", "ListNode", false, p("head", "ListNode")),
                List.of("[42]"));
        assertThat(out).isEqualTo("[42]\n");
    }

    @Test
    void list_isPalindrome_returns_boolean() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    bool isPalindrome(ListNode* head) {\n" +
                "        std::vector<int> v; while (head) { v.push_back(head->val); head = head->next; }\n" +
                "        for (int i = 0, j = v.size() - 1; i < j; i++, j--)\n" +
                "            if (v[i] != v[j]) return false;\n" +
                "        return true;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("isPalindrome", "boolean", false, p("head", "ListNode"));
        assertThat(runCpp(code, m, List.of("[1,2,2,1]"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("[1,2,3]"))).isEqualTo("false\n");
        assertThat(runCpp(code, m, List.of("[]"))).isEqualTo("true\n");
    }

    @Test
    void list_middleValue_returns_int() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    int middle(ListNode* head) {\n" +
                "        ListNode* slow = head; ListNode* fast = head;\n" +
                "        while (fast && fast->next) { slow = slow->next; fast = fast->next->next; }\n" +
                "        return slow ? slow->val : -1;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("middle", "int", false, p("head", "ListNode"));
        assertThat(runCpp(code, m, List.of("[1,2,3,4,5]"))).isEqualTo("3\n");
        assertThat(runCpp(code, m, List.of("[1,2,3,4]"))).isEqualTo("3\n");
    }

    @Test
    void list_mergeTwoSorted_returns_listnode() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* merge(ListNode* a, ListNode* b) {\n" +
                "        ListNode dummy(0); ListNode* cur = &dummy;\n" +
                "        while (a && b) {\n" +
                "            if (a->val <= b->val) { cur->next = a; a = a->next; }\n" +
                "            else { cur->next = b; b = b->next; }\n" +
                "            cur = cur->next;\n" +
                "        }\n" +
                "        cur->next = a ? a : b;\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("merge", "ListNode", false,
                        p("a", "ListNode"), p("b", "ListNode")),
                List.of("[1,2,4]", "[1,3,4]"));
        assertThat(out).isEqualTo("[1,1,2,3,4,4]\n");
    }

    @Test
    void list_alternating_values() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* echo(ListNode* head) {\n" +
                "        ListNode dummy(0); ListNode* cur = &dummy;\n" +
                "        while (head) { cur->next = new ListNode(head->val); cur = cur->next; head = head->next; }\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("echo", "ListNode", false, p("head", "ListNode")),
                List.of("[1,-1,1,-1,1]"));
        assertThat(out).isEqualTo("[1,-1,1,-1,1]\n");
    }

    // ── Multi-param / mixed-DS combinations ───────────────────────────────

    @Test
    void multi_param_int_array_int_int_returns_int_array() throws Exception {
        // Subarray sum range — exercises 3 params of mixed types.
        String code =
                "class Solution { public:\n" +
                "    std::vector<int> slice(std::vector<int>& a, int from, int to) {\n" +
                "        return std::vector<int>(a.begin() + from, a.begin() + to);\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("slice", "int[]", false,
                        p("a", "int[]"), p("from", "int"), p("to", "int")),
                List.of("[10,20,30,40,50]", "1", "4"));
        assertThat(out).isEqualTo("[20,30,40]\n");
    }

    @Test
    void four_param_mix_tree_int_string_bool() throws Exception {
        // TreeNode + int + String + boolean — 4 params, all distinct DS / scalars.
        String code =
                "class Solution { public:\n" +
                "    std::string describe(TreeNode* root, int target, std::string label, bool verbose) {\n" +
                "        std::string val = root ? std::to_string(root->val) : std::string(\"null\");\n" +
                "        std::string r = label + \"=\" + val + \",t=\" + std::to_string(target);\n" +
                "        if (verbose) r += \"!\";\n" +
                "        return r;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("describe", "String", false,
                p("root", "TreeNode"), p("target", "int"),
                p("label", "String"), p("verbose", "boolean"));
        assertThat(runCpp(code, m, List.of("[7,1,2]", "9", "root", "true")))
                .isEqualTo("root=7,t=9!\n");
        assertThat(runCpp(code, m, List.of("[]", "0", "root", "false")))
                .isEqualTo("root=null,t=0\n");
    }

    @Test
    void listnode_plus_int_returns_listnode() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    ListNode* removeAll(ListNode* head, int val) {\n" +
                "        ListNode dummy(0); dummy.next = head; ListNode* cur = &dummy;\n" +
                "        while (cur->next) {\n" +
                "            if (cur->next->val == val) cur->next = cur->next->next;\n" +
                "            else cur = cur->next;\n" +
                "        }\n" +
                "        return dummy.next;\n" +
                "    }\n" +
                "};";
        String out = runCpp(code, meta("removeAll", "ListNode", false,
                        p("head", "ListNode"), p("val", "int")),
                List.of("[1,2,6,3,4,5,6]", "6"));
        assertThat(out).isEqualTo("[1,2,3,4,5]\n");
    }

    @Test
    void int_array_plus_int_returns_boolean() throws Exception {
        String code =
                "class Solution { public:\n" +
                "    bool contains(std::vector<int>& a, int x) {\n" +
                "        for (int v : a) if (v == x) return true;\n" +
                "        return false;\n" +
                "    }\n" +
                "};";
        FunctionMeta m = meta("contains", "boolean", false,
                p("a", "int[]"), p("x", "int"));
        assertThat(runCpp(code, m, List.of("[1,2,3,4,5]", "3"))).isEqualTo("true\n");
        assertThat(runCpp(code, m, List.of("[1,2,3,4,5]", "9"))).isEqualTo("false\n");
        assertThat(runCpp(code, m, List.of("[]", "0"))).isEqualTo("false\n");
    }
}
