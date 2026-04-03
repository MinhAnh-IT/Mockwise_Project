package com.interview.judge.codebuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSerializerTest {

    private TypeSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new TypeSerializer(new ObjectMapper());
    }

    // ── Primitives ────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "int,     42,    42",
        "long,    100,   100",
        "double,  3.14,  3.14",
        "boolean, true,  true",
    })
    void primitiveTypes_returnRawValue(String type, Object value, String expected) throws Exception {
        assertThat(serializer.serialize(value, type)).isEqualTo(expected);
    }

    @Test
    void int_negative() throws Exception {
        assertThat(serializer.serialize(-7, "int")).isEqualTo("-7");
    }

    @Test
    void int_zero() throws Exception {
        assertThat(serializer.serialize(0, "int")).isEqualTo("0");
    }

    @Test
    void int_maxValue() throws Exception {
        assertThat(serializer.serialize(Integer.MAX_VALUE, "int")).isEqualTo("2147483647");
    }

    @Test
    void int_minValue() throws Exception {
        assertThat(serializer.serialize(Integer.MIN_VALUE, "int")).isEqualTo("-2147483648");
    }

    @Test
    void long_largeValue() throws Exception {
        assertThat(serializer.serialize(9_000_000_000L, "long")).isEqualTo("9000000000");
    }

    @Test
    void double_negative() throws Exception {
        assertThat(serializer.serialize(-0.001, "double")).isEqualTo("-0.001");
    }

    @Test
    void boolean_false() throws Exception {
        assertThat(serializer.serialize(false, "boolean")).isEqualTo("false");
    }

    // ── String (both lowercase and Java-style capital) ────────────────────

    @Test
    void string_lowercase_noQuotes() throws Exception {
        assertThat(serializer.serialize("hello", "string")).isEqualTo("hello");
    }

    @Test
    void string_capitalS_noQuotes() throws Exception {
        assertThat(serializer.serialize("()", "String")).isEqualTo("()");
    }

    @Test
    void string_withBrackets_noQuotes() throws Exception {
        assertThat(serializer.serialize("()[]{}", "String")).isEqualTo("()[]{}");
    }

    @Test
    void string_empty() throws Exception {
        assertThat(serializer.serialize("", "String")).isEqualTo("");
    }

    @Test
    void string_singleChar() throws Exception {
        assertThat(serializer.serialize("a", "String")).isEqualTo("a");
    }

    @Test
    void string_withSpaces() throws Exception {
        assertThat(serializer.serialize("hello world", "String")).isEqualTo("hello world");
    }

    // ── int[] ─────────────────────────────────────────────────────────────

    @Test
    void intArray_serializedAsJsonArray() throws Exception {
        assertThat(serializer.serialize(List.of(2, 7, 11, 15), "int[]")).isEqualTo("[2,7,11,15]");
    }

    @Test
    void intArray_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "int[]")).isEqualTo("[]");
    }

    @Test
    void intArray_singleElement() throws Exception {
        assertThat(serializer.serialize(List.of(42), "int[]")).isEqualTo("[42]");
    }

    @Test
    void intArray_withNegatives() throws Exception {
        assertThat(serializer.serialize(List.of(-1, 0, 1), "int[]")).isEqualTo("[-1,0,1]");
    }

    // ── long[] ────────────────────────────────────────────────────────────

    @Test
    void longArray_serializedAsJsonArray() throws Exception {
        assertThat(serializer.serialize(List.of(9_000_000_000L, 1L), "long[]"))
                .isEqualTo("[9000000000,1]");
    }

    @Test
    void longArray_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "long[]")).isEqualTo("[]");
    }

    // ── double[] ─────────────────────────────────────────────────────────

    @Test
    void doubleArray_serializedAsJsonArray() throws Exception {
        assertThat(serializer.serialize(List.of(1.5, 2.5, 3.0), "double[]"))
                .isEqualTo("[1.5,2.5,3.0]");
    }

    @Test
    void doubleArray_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "double[]")).isEqualTo("[]");
    }

    // ── String[] ─────────────────────────────────────────────────────────

    @Test
    void stringArray_serializedWithQuotes() throws Exception {
        assertThat(serializer.serialize(List.of("a", "b"), "string[]"))
                .isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    void stringArray_capitalS_serializedWithQuotes() throws Exception {
        assertThat(serializer.serialize(List.of("flower", "flow", "flight"), "String[]"))
                .isEqualTo("[\"flower\",\"flow\",\"flight\"]");
    }

    @Test
    void stringArray_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "String[]")).isEqualTo("[]");
    }

    @Test
    void stringArray_containsEmptyString() throws Exception {
        // Common edge case: longestCommonPrefix(["","a"]) → prefix is ""
        assertThat(serializer.serialize(List.of("", "a"), "String[]"))
                .isEqualTo("[\"\",\"a\"]");
    }

    @Test
    void stringArray_singleElement() throws Exception {
        assertThat(serializer.serialize(List.of("hello"), "String[]"))
                .isEqualTo("[\"hello\"]");
    }

    // ── int[][] ───────────────────────────────────────────────────────────

    @Test
    void intMatrix_3x3() throws Exception {
        List<List<Integer>> matrix = List.of(
                List.of(1, 2, 3),
                List.of(4, 5, 6),
                List.of(7, 8, 9)
        );
        assertThat(serializer.serialize(matrix, "int[][]"))
                .isEqualTo("[[1,2,3],[4,5,6],[7,8,9]]");
    }

    @Test
    void intMatrix_1x1() throws Exception {
        assertThat(serializer.serialize(List.of(List.of(42)), "int[][]"))
                .isEqualTo("[[42]]");
    }

    @Test
    void intMatrix_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "int[][]")).isEqualTo("[]");
    }

    @Test
    void intMatrix_withNegatives() throws Exception {
        List<List<Integer>> matrix = List.of(List.of(-1, 2), List.of(-3, 4));
        assertThat(serializer.serialize(matrix, "int[][]"))
                .isEqualTo("[[-1,2],[-3,4]]");
    }

    // ── char[][] ─────────────────────────────────────────────────────────
    // Jackson deserializes char[][] input as List<List<String>> (each string = 1 char)

    @Test
    void charMatrix_wordSearchBoard() throws Exception {
        List<List<String>> board = List.of(
                List.of("A", "B", "C"),
                List.of("D", "E", "F")
        );
        assertThat(serializer.serialize(board, "char[][]"))
                .isEqualTo("[[\"A\",\"B\",\"C\"],[\"D\",\"E\",\"F\"]]");
    }

    @Test
    void charMatrix_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "char[][]")).isEqualTo("[]");
    }

    @Test
    void charMatrix_dotGrid() throws Exception {
        // Typical BFS/DFS grid with '.' cells
        List<List<String>> board = List.of(List.of(".", ".", "."));
        assertThat(serializer.serialize(board, "char[][]"))
                .isEqualTo("[[\".\",\".\",\".\"]]");
    }

    // ── TreeNode ──────────────────────────────────────────────────────────
    // Input arrives from Kafka as a Jackson-deserialized List (level-order array with nulls)

    @Test
    void treeNode_normalTree() throws Exception {
        // [3,9,20,null,null,15,7]
        List<Object> levelOrder = Arrays.asList(3, 9, 20, null, null, 15, 7);
        assertThat(serializer.serialize(levelOrder, "TreeNode"))
                .isEqualTo("[3,9,20,null,null,15,7]");
    }

    @Test
    void treeNode_singleNode() throws Exception {
        assertThat(serializer.serialize(List.of(1), "TreeNode"))
                .isEqualTo("[1]");
    }

    @Test
    void treeNode_emptyArray() throws Exception {
        assertThat(serializer.serialize(List.of(), "TreeNode")).isEqualTo("[]");
    }

    @Test
    void treeNode_skewedLeft() throws Exception {
        // [1,2,null,3,null]
        List<Object> levelOrder = Arrays.asList(1, 2, null, 3, null);
        assertThat(serializer.serialize(levelOrder, "TreeNode"))
                .isEqualTo("[1,2,null,3,null]");
    }

    // ── ListNode ──────────────────────────────────────────────────────────
    // Input arrives from Kafka as a List<Integer>

    @Test
    void listNode_normalList() throws Exception {
        assertThat(serializer.serialize(List.of(1, 2, 3, 4, 5), "ListNode"))
                .isEqualTo("[1,2,3,4,5]");
    }

    @Test
    void listNode_singleElement() throws Exception {
        assertThat(serializer.serialize(List.of(1), "ListNode")).isEqualTo("[1]");
    }

    @Test
    void listNode_emptyArray() throws Exception {
        assertThat(serializer.serialize(List.of(), "ListNode")).isEqualTo("[]");
    }

    @Test
    void listNode_withNegatives() throws Exception {
        assertThat(serializer.serialize(List.of(-1, 0, 1), "ListNode"))
                .isEqualTo("[-1,0,1]");
    }

    // ── char / Character ─────────────────────────────────────────────────

    @Test
    void char_lowercase_sentAsRawChar() throws Exception {
        assertThat(serializer.serialize("A", "char")).isEqualTo("A");
    }

    @Test
    void char_capital_Character_sentAsRawChar() throws Exception {
        assertThat(serializer.serialize("z", "Character")).isEqualTo("z");
    }

    // ── String[][] ───────────────────────────────────────────────────────

    @Test
    void stringMatrix_serializedAsNestedJsonArray() throws Exception {
        List<List<String>> matrix = List.of(List.of("eat", "tea"), List.of("tan", "nat"));
        assertThat(serializer.serialize(matrix, "String[][]"))
                .isEqualTo("[[\"eat\",\"tea\"],[\"tan\",\"nat\"]]");
    }

    @Test
    void stringMatrix_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "String[][]")).isEqualTo("[]");
    }

    // ── List<Integer> ────────────────────────────────────────────────────

    @Test
    void listInteger_serializedAsJsonArray() throws Exception {
        assertThat(serializer.serialize(List.of(1, 2, 3), "List<Integer>"))
                .isEqualTo("[1,2,3]");
    }

    @Test
    void listInteger_empty() throws Exception {
        assertThat(serializer.serialize(List.of(), "List<Integer>")).isEqualTo("[]");
    }

    // ── List<String> ─────────────────────────────────────────────────────

    @Test
    void listString_serializedAsJsonStringArray() throws Exception {
        assertThat(serializer.serialize(List.of("eat", "tea"), "List<String>"))
                .isEqualTo("[\"eat\",\"tea\"]");
    }

    // ── List<List<Integer>> ──────────────────────────────────────────────

    @Test
    void listListInteger_serializedAsNestedJsonArray() throws Exception {
        List<List<Integer>> nested = List.of(List.of(0, 1), List.of(2, 3));
        assertThat(serializer.serialize(nested, "List<List<Integer>>"))
                .isEqualTo("[[0,1],[2,3]]");
    }

    // ── List<List<String>> ───────────────────────────────────────────────

    @Test
    void listListString_serializedAsNestedJsonStringArray() throws Exception {
        List<List<String>> nested = List.of(List.of("bat"), List.of("nat", "tan"));
        assertThat(serializer.serialize(nested, "List<List<String>>"))
                .isEqualTo("[[\"bat\"],[\"nat\",\"tan\"]]");
    }

    // ── Null ─────────────────────────────────────────────────────────────

    @Test
    void nullValue_returnsNullString() throws Exception {
        assertThat(serializer.serialize(null, "int")).isEqualTo("null");
    }

    @Test
    void nullValue_arrayType_returnsNullString() throws Exception {
        assertThat(serializer.serialize(null, "int[]")).isEqualTo("null");
    }

    @Test
    void nullValue_treeNodeType_returnsNullString() throws Exception {
        assertThat(serializer.serialize(null, "TreeNode")).isEqualTo("null");
    }
}
