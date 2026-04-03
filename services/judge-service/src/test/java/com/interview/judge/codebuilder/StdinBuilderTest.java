package com.interview.judge.codebuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StdinBuilderTest {

    private StdinBuilder stdinBuilder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        stdinBuilder = new StdinBuilder(objectMapper, new TypeSerializer(objectMapper));
    }

    // ── Primitives ────────────────────────────────────────────────────────

    @Test
    void twoSum_intArrayAndInt_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("twoSum")
                .params(List.of(
                        ParamMeta.builder().name("nums").type("int[]").build(),
                        ParamMeta.builder().name("target").type("int").build()
                ))
                .returnType("int[]").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(2, 7, 11, 15), "target", 9));
        String[] lines = stdin.split("\n");

        assertThat(lines[0]).contains("\"fn\":\"twoSum\"");
        assertThat(lines[1]).isEqualTo("[2,7,11,15]");
        assertThat(lines[2]).isEqualTo("9");
    }

    @Test
    void int_negativeValue_appearsInStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("abs")
                .params(List.of(ParamMeta.builder().name("n").type("int").build()))
                .returnType("int").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("n", -7));
        assertThat(stdin.split("\n")[1]).isEqualTo("-7");
    }

    @Test
    void long_param_serializedCorrectly() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("largeSum")
                .params(List.of(ParamMeta.builder().name("n").type("long").build()))
                .returnType("long").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("n", 9_000_000_000L));
        assertThat(stdin.split("\n")[1]).isEqualTo("9000000000");
    }

    @Test
    void double_param_serializedCorrectly() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("sqrt")
                .params(List.of(ParamMeta.builder().name("x").type("double").build()))
                .returnType("double").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("x", 3.14));
        assertThat(stdin.split("\n")[1]).isEqualTo("3.14");
    }

    @Test
    void boolean_param_serializedCorrectly() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("flip")
                .params(List.of(ParamMeta.builder().name("flag").type("boolean").build()))
                .returnType("boolean").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("flag", true));
        assertThat(stdin.split("\n")[1]).isEqualTo("true");
    }

    // ── String ────────────────────────────────────────────────────────────

    @Test
    void string_param_noQuotesInStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("isValid")
                .params(List.of(ParamMeta.builder().name("s").type("String").build()))
                .returnType("boolean").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("s", "()"));
        assertThat(stdin.split("\n")[1]).isEqualTo("()");
    }

    @Test
    void string_emptyParam_appearsAsEmptyLine() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("reverse")
                .params(List.of(ParamMeta.builder().name("s").type("String").build()))
                .returnType("String").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("s", ""));
        assertThat(stdin.split("\n", -1)[1]).isEqualTo("");
    }

    // ── Arrays ────────────────────────────────────────────────────────────

    @Test
    void stringArray_serializedWithQuotes() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("longestCommonPrefix")
                .params(List.of(ParamMeta.builder().name("strs").type("String[]").build()))
                .returnType("String").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("strs", List.of("flower", "flow", "flight")));
        assertThat(stdin.split("\n")[1]).isEqualTo("[\"flower\",\"flow\",\"flight\"]");
    }

    @Test
    void stringArray_empty_serializedAsEmptyArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("longestCommonPrefix")
                .params(List.of(ParamMeta.builder().name("strs").type("String[]").build()))
                .returnType("String").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("strs", List.of()));
        assertThat(stdin.split("\n")[1]).isEqualTo("[]");
    }

    @Test
    void longArray_serializedCorrectly() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("productExceptSelf")
                .params(List.of(ParamMeta.builder().name("nums").type("long[]").build()))
                .returnType("long[]").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(9_000_000_000L, 1L)));
        assertThat(stdin.split("\n")[1]).isEqualTo("[9000000000,1]");
    }

    @Test
    void doubleArray_serializedCorrectly() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("normalize")
                .params(List.of(ParamMeta.builder().name("nums").type("double[]").build()))
                .returnType("double[]").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(1.5, 2.5)));
        assertThat(stdin.split("\n")[1]).isEqualTo("[1.5,2.5]");
    }

    // ── int[][] ───────────────────────────────────────────────────────────

    @Test
    void intMatrix_spiralOrder_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("spiralOrder")
                .params(List.of(ParamMeta.builder().name("matrix").type("int[][]").build()))
                .returnType("int[]").inPlace(false).build();

        List<List<Integer>> matrix = List.of(
                List.of(1, 2, 3),
                List.of(4, 5, 6),
                List.of(7, 8, 9)
        );
        String stdin = stdinBuilder.build(meta, Map.of("matrix", matrix));
        String[] lines = stdin.split("\n");

        assertThat(lines[0]).contains("\"fn\":\"spiralOrder\"");
        assertThat(lines[1]).isEqualTo("[[1,2,3],[4,5,6],[7,8,9]]");
    }

    @Test
    void intMatrix_empty_serializedAsEmptyArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("spiralOrder")
                .params(List.of(ParamMeta.builder().name("matrix").type("int[][]").build()))
                .returnType("int[]").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("matrix", List.of()));
        assertThat(stdin.split("\n")[1]).isEqualTo("[]");
    }

    // ── char[][] ─────────────────────────────────────────────────────────

    @Test
    void charMatrix_wordSearch_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("exist")
                .params(List.of(
                        ParamMeta.builder().name("board").type("char[][]").build(),
                        ParamMeta.builder().name("word").type("String").build()
                ))
                .returnType("boolean").inPlace(false).build();

        List<List<String>> board = List.of(
                List.of("A", "B", "C", "E"),
                List.of("S", "F", "C", "S"),
                List.of("A", "D", "E", "E")
        );
        String stdin = stdinBuilder.build(meta, Map.of("board", board, "word", "ABCCED"));
        String[] lines = stdin.split("\n");

        assertThat(lines[1]).isEqualTo("[[\"A\",\"B\",\"C\",\"E\"],[\"S\",\"F\",\"C\",\"S\"],[\"A\",\"D\",\"E\",\"E\"]]");
        assertThat(lines[2]).isEqualTo("ABCCED");
    }

    // ── TreeNode ──────────────────────────────────────────────────────────

    @Test
    void treeNode_normalTree_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("maxDepth")
                .params(List.of(ParamMeta.builder().name("root").type("TreeNode").build()))
                .returnType("int").inPlace(false).build();

        List<Object> levelOrder = Arrays.asList(3, 9, 20, null, null, 15, 7);
        String stdin = stdinBuilder.build(meta, Map.of("root", levelOrder));
        String[] lines = stdin.split("\n");

        assertThat(lines[0]).contains("\"fn\":\"maxDepth\"");
        assertThat(lines[0]).contains("\"return\":\"int\"");
        assertThat(lines[1]).isEqualTo("[3,9,20,null,null,15,7]");
    }

    @Test
    void treeNode_emptyTree_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("maxDepth")
                .params(List.of(ParamMeta.builder().name("root").type("TreeNode").build()))
                .returnType("int").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("root", List.of()));
        assertThat(stdin.split("\n")[1]).isEqualTo("[]");
    }

    // ── ListNode ──────────────────────────────────────────────────────────

    @Test
    void listNode_reverseList_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("reverseList")
                .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                .returnType("ListNode").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("head", List.of(1, 2, 3, 4, 5)));
        String[] lines = stdin.split("\n");

        assertThat(lines[0]).contains("\"fn\":\"reverseList\"");
        assertThat(lines[0]).contains("\"return\":\"ListNode\"");
        assertThat(lines[1]).isEqualTo("[1,2,3,4,5]");
    }

    @Test
    void listNode_emptyList_buildsCorrectStdin() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("reverseList")
                .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                .returnType("ListNode").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("head", List.of()));
        assertThat(stdin.split("\n")[1]).isEqualTo("[]");
    }

    // ── Meta JSON content ─────────────────────────────────────────────────

    @Test
    void metaJson_containsReturnType() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("reverseList")
                .params(List.of(ParamMeta.builder().name("head").type("ListNode").build()))
                .returnType("ListNode").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("head", List.of(1)));
        // Driver uses "return" field to handle null-result serialization for ListNode/TreeNode
        assertThat(stdin.split("\n")[0]).contains("\"return\":\"ListNode\"");
    }

    @Test
    void metaJson_inPlaceTrue_appearsInMetaLine() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("sortColors")
                .params(List.of(ParamMeta.builder().name("nums").type("int[]").build()))
                .returnType("void").inPlace(true).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(2, 0, 1)));
        assertThat(stdin.split("\n")[0]).contains("\"inPlace\":true");
    }

    @Test
    void params_orderFollowsFunctionMetaNotInputMapOrder() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("twoProduct")
                .params(List.of(
                        ParamMeta.builder().name("nums").type("int[]").build(),
                        ParamMeta.builder().name("target").type("int").build()
                ))
                .returnType("int[]").inPlace(false).build();

        // Map keys are in different order than params
        String stdin = stdinBuilder.build(meta, Map.of("target", 8, "nums", List.of(2, 4, 1, 6, 5)));
        String[] lines = stdin.split("\n");

        assertThat(lines[1]).isEqualTo("[2,4,1,6,5]");
        assertThat(lines[2]).isEqualTo("8");
    }

    // ── char ─────────────────────────────────────────────────────────────

    @Test
    void char_param_sentAsRawCharWithoutQuotes() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("toUpperCase")
                .params(List.of(ParamMeta.builder().name("c").type("char").build()))
                .returnType("char").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("c", "a"));
        assertThat(stdin.split("\n")[1]).isEqualTo("a");
    }

    // ── String[][] ───────────────────────────────────────────────────────

    @Test
    void stringMatrix_serializedAsNestedJsonArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("groupAnagrams")
                .params(List.of(ParamMeta.builder().name("words").type("String[][]").build()))
                .returnType("List<List<String>>").inPlace(false).build();

        List<List<String>> matrix = List.of(List.of("eat", "tea"), List.of("tan"));
        String stdin = stdinBuilder.build(meta, Map.of("words", matrix));
        assertThat(stdin.split("\n")[1]).isEqualTo("[[\"eat\",\"tea\"],[\"tan\"]]");
    }

    // ── List<Integer> ────────────────────────────────────────────────────

    @Test
    void listInteger_param_serializedAsJsonArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("sumList")
                .params(List.of(ParamMeta.builder().name("nums").type("List<Integer>").build()))
                .returnType("int").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(1, 2, 3)));
        assertThat(stdin.split("\n")[0]).contains("\"return\":\"int\"");
        assertThat(stdin.split("\n")[1]).isEqualTo("[1,2,3]");
    }

    // ── List<String> ─────────────────────────────────────────────────────

    @Test
    void listString_param_serializedAsJsonStringArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("join")
                .params(List.of(ParamMeta.builder().name("words").type("List<String>").build()))
                .returnType("String").inPlace(false).build();

        String stdin = stdinBuilder.build(meta, Map.of("words", List.of("hello", "world")));
        assertThat(stdin.split("\n")[1]).isEqualTo("[\"hello\",\"world\"]");
    }

    // ── List<List<Integer>> ──────────────────────────────────────────────

    @Test
    void listListInteger_param_serializedAsNestedJsonArray() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("flatten")
                .params(List.of(ParamMeta.builder().name("nums").type("List<List<Integer>>").build()))
                .returnType("List<Integer>").inPlace(false).build();

        List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3, 4));
        String stdin = stdinBuilder.build(meta, Map.of("nums", nested));
        assertThat(stdin.split("\n")[1]).isEqualTo("[[1,2],[3,4]]");
    }

    // ── inPlace ───────────────────────────────────────────────────────────

    @Test
    void inPlace_sortColors_stdinHasArrayOnLine2() throws Exception {
        FunctionMeta meta = FunctionMeta.builder()
                .fn("sortColors")
                .params(List.of(ParamMeta.builder().name("nums").type("int[]").build()))
                .returnType("void").inPlace(true).build();

        String stdin = stdinBuilder.build(meta, Map.of("nums", List.of(2, 0, 2, 1, 1, 0)));
        assertThat(stdin.split("\n")[1]).isEqualTo("[2,0,2,1,1,0]");
    }
}
