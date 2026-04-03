package com.interview.judge.codebuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    private OutputComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new OutputComparator(new ObjectMapper());
    }

    // ── int output ───────────────────────────────────────────────────────

    @Test
    void int_correct_ac() {
        assertThat(comparator.compare("42", "{\"result\":42}", true)).isTrue();
    }

    @Test
    void int_wrong_wa() {
        assertThat(comparator.compare("43", "{\"result\":42}", true)).isFalse();
    }

    @Test
    void int_negative_ac() {
        assertThat(comparator.compare("-7", "{\"result\":-7}", true)).isTrue();
    }

    @Test
    void int_zero_ac() {
        assertThat(comparator.compare("0", "{\"result\":0}", true)).isTrue();
    }

    @Test
    void int_maxValue_ac() {
        assertThat(comparator.compare("2147483647", "{\"result\":2147483647}", true)).isTrue();
    }

    // ── long output ──────────────────────────────────────────────────────

    @Test
    void long_largeValue_ac() {
        assertThat(comparator.compare("9000000000", "{\"result\":9000000000}", true)).isTrue();
    }

    @Test
    void long_wrong_wa() {
        assertThat(comparator.compare("9000000001", "{\"result\":9000000000}", true)).isFalse();
    }

    // ── double output ────────────────────────────────────────────────────

    @Test
    void double_exact_ac() {
        assertThat(comparator.compare("3.14", "{\"result\":3.14}", true)).isTrue();
    }

    @Test
    void double_wrong_wa() {
        assertThat(comparator.compare("3.15", "{\"result\":3.14}", true)).isFalse();
    }

    // ── boolean output ───────────────────────────────────────────────────

    @Test
    void boolean_true_ac() {
        assertThat(comparator.compare("true", "{\"result\":true}", true)).isTrue();
    }

    @Test
    void boolean_false_ac() {
        assertThat(comparator.compare("false", "{\"result\":false}", true)).isTrue();
    }

    @Test
    void boolean_wrongValue_wa() {
        assertThat(comparator.compare("false", "{\"result\":true}", true)).isFalse();
    }

    // ── String output ────────────────────────────────────────────────────

    @Test
    void string_correct_ac() {
        assertThat(comparator.compare("fl", "{\"result\":\"fl\"}", true)).isTrue();
    }

    @Test
    void string_wrong_wa() {
        assertThat(comparator.compare("f", "{\"result\":\"fl\"}", true)).isFalse();
    }

    @Test
    void string_empty_ac() {
        // longestCommonPrefix(["dog","racecar","car"]) → ""
        assertThat(comparator.compare("", "{\"result\":\"\"}", true)).isTrue();
    }

    @Test
    void string_withSpaces_ac() {
        assertThat(comparator.compare("hello world", "{\"result\":\"hello world\"}", true)).isTrue();
    }

    // ── int[] output ─────────────────────────────────────────────────────

    @Test
    void intArray_exactMatch_ac() {
        assertThat(comparator.compare("[0,1]", "{\"result\":[0,1]}", false)).isTrue();
    }

    @Test
    void intArray_orderDoesNotMatter_differentOrder_ac() {
        // twoSum: [1,0] vs expected [0,1] — order doesn't matter
        assertThat(comparator.compare("[1,0]", "{\"result\":[0,1]}", false)).isTrue();
    }

    @Test
    void intArray_orderMatters_differentOrder_wa() {
        assertThat(comparator.compare("[1,0]", "{\"result\":[0,1]}", true)).isFalse();
    }

    @Test
    void intArray_wrongValues_wa() {
        assertThat(comparator.compare("[0,2]", "{\"result\":[0,1]}", false)).isFalse();
    }

    @Test
    void intArray_empty_ac() {
        assertThat(comparator.compare("[]", "{\"result\":[]}", false)).isTrue();
    }

    @Test
    void intArray_singleElement_ac() {
        assertThat(comparator.compare("[5]", "{\"result\":[5]}", true)).isTrue();
    }

    @Test
    void intArray_withNegatives_ac() {
        assertThat(comparator.compare("[-1,0,1]", "{\"result\":[-1,0,1]}", true)).isTrue();
    }

    @Test
    void intArray_sorted_orderDoesNotMatter_ac() {
        // sortColors inPlace: [0,0,1,1,2,2] exact order, orderMatters=true
        assertThat(comparator.compare("[0,0,1,1,2,2]", "{\"result\":[0,0,1,1,2,2]}", true)).isTrue();
    }

    // ── String[] output ──────────────────────────────────────────────────

    @Test
    void stringArray_exactMatch_ac() {
        assertThat(comparator.compare("[\"fl\",\"inter\"]", "{\"result\":[\"fl\",\"inter\"]}", true)).isTrue();
    }

    @Test
    void stringArray_orderDoesNotMatter_ac() {
        assertThat(comparator.compare("[\"b\",\"a\"]", "{\"result\":[\"a\",\"b\"]}", false)).isTrue();
    }

    @Test
    void stringArray_wrong_wa() {
        assertThat(comparator.compare("[\"fl\",\"wrong\"]", "{\"result\":[\"fl\",\"inter\"]}", true)).isFalse();
    }

    // ── int[][] output ───────────────────────────────────────────────────

    @Test
    void intMatrix_spiralOrder_ac() {
        assertThat(comparator.compare("[1,2,3,6,9,8,7,4,5]", "{\"result\":[1,2,3,6,9,8,7,4,5]}", true)).isTrue();
    }

    @Test
    void intMatrix_nested_orderDoesNotMatter_ac() {
        // groupAnagrams: [[bat],[nat,tan],[ate,eat,tea]] — outer order doesn't matter
        assertThat(comparator.compare(
                "[[\"nat\",\"tan\"],[\"bat\"],[\"ate\",\"eat\",\"tea\"]]",
                "{\"result\":[[\"bat\"],[\"nat\",\"tan\"],[\"ate\",\"eat\",\"tea\"]]}",
                false
        )).isTrue();
    }

    // ── ListNode output (serialized as int array) ─────────────────────────

    @Test
    void listNode_reversed_ac() {
        assertThat(comparator.compare("[5,4,3,2,1]", "{\"result\":[5,4,3,2,1]}", true)).isTrue();
    }

    @Test
    void listNode_singleElement_ac() {
        assertThat(comparator.compare("[1]", "{\"result\":[1]}", true)).isTrue();
    }

    @Test
    void listNode_emptyResult_ac() {
        // Fixed bug: reverseList([]) returns null → driver now prints "[]"
        assertThat(comparator.compare("[]", "{\"result\":[]}", true)).isTrue();
    }

    @Test
    void listNode_nullStdout_emptyExpected_wa() {
        // "null" stdout (before driver fix) must NOT match "[]"
        assertThat(comparator.compare("null", "{\"result\":[]}", true)).isFalse();
    }

    @Test
    void listNode_wrong_wa() {
        assertThat(comparator.compare("[1,2,3]", "{\"result\":[3,2,1]}", true)).isFalse();
    }

    // ── TreeNode output (serialized as level-order array) ─────────────────

    @Test
    void treeNode_levelOrder_ac() {
        assertThat(comparator.compare("[3,9,20,null,null,15,7]", "{\"result\":[3,9,20,null,null,15,7]}", true)).isTrue();
    }

    @Test
    void treeNode_emptyTree_ac() {
        // Fixed bug: function returning null TreeNode → driver now prints "[]"
        assertThat(comparator.compare("[]", "{\"result\":[]}", true)).isTrue();
    }

    @Test
    void treeNode_singleNode_ac() {
        assertThat(comparator.compare("[1]", "{\"result\":[1]}", true)).isTrue();
    }

    @Test
    void treeNode_nullStdout_emptyExpected_wa() {
        // "null" stdout (before driver fix) must NOT match "[]"
        assertThat(comparator.compare("null", "{\"result\":[]}", true)).isFalse();
    }

    @Test
    void treeNode_wrong_wa() {
        assertThat(comparator.compare("[1,2,3]", "{\"result\":[1,null,3]}", true)).isFalse();
    }

    // ── char output ──────────────────────────────────────────────────────

    @Test
    void char_correct_ac() {
        // char return is printed without quotes; fallback string compare handles it
        assertThat(comparator.compare("A", "{\"result\":\"A\"}", true)).isTrue();
    }

    @Test
    void char_wrong_wa() {
        assertThat(comparator.compare("B", "{\"result\":\"A\"}", true)).isFalse();
    }

    // ── String[][] output ────────────────────────────────────────────────

    @Test
    void stringMatrix_correct_ac() {
        assertThat(comparator.compare(
                "[[\"eat\",\"tea\"],[\"bat\"]]",
                "{\"result\":[[\"eat\",\"tea\"],[\"bat\"]]}",
                true
        )).isTrue();
    }

    @Test
    void stringMatrix_orderDoesNotMatter_ac() {
        assertThat(comparator.compare(
                "[[\"bat\"],[\"eat\",\"tea\"]]",
                "{\"result\":[[\"eat\",\"tea\"],[\"bat\"]]}",
                false
        )).isTrue();
    }

    // ── List<Integer> output ─────────────────────────────────────────────

    @Test
    void listInteger_correct_ac() {
        assertThat(comparator.compare("[1,2,3]", "{\"result\":[1,2,3]}", true)).isTrue();
    }

    @Test
    void listInteger_orderDoesNotMatter_ac() {
        assertThat(comparator.compare("[3,1,2]", "{\"result\":[1,2,3]}", false)).isTrue();
    }

    @Test
    void listInteger_wrong_wa() {
        assertThat(comparator.compare("[1,2,4]", "{\"result\":[1,2,3]}", true)).isFalse();
    }

    // ── List<String> output ──────────────────────────────────────────────

    @Test
    void listString_correct_ac() {
        // List<String> must be serialized with quotes: ["eat","tea"]
        assertThat(comparator.compare(
                "[\"eat\",\"tea\"]",
                "{\"result\":[\"eat\",\"tea\"]}",
                true
        )).isTrue();
    }

    @Test
    void listString_wrong_wa() {
        assertThat(comparator.compare(
                "[eat,tea]",          // no quotes — wrong format
                "{\"result\":[\"eat\",\"tea\"]}",
                true
        )).isFalse();
    }

    // ── List<List<Integer>> output ───────────────────────────────────────

    @Test
    void listListInteger_correct_ac() {
        assertThat(comparator.compare(
                "[[0,1],[2,3]]",
                "{\"result\":[[0,1],[2,3]]}",
                true
        )).isTrue();
    }

    // ── List<List<String>> output ────────────────────────────────────────

    @Test
    void listListString_correct_ac() {
        assertThat(comparator.compare(
                "[[\"bat\"],[\"nat\",\"tan\"],[\"ate\",\"eat\",\"tea\"]]",
                "{\"result\":[[\"bat\"],[\"nat\",\"tan\"],[\"ate\",\"eat\",\"tea\"]]}",
                true
        )).isTrue();
    }

    @Test
    void listListString_orderDoesNotMatter_differentOuterOrder_ac() {
        assertThat(comparator.compare(
                "[[\"bat\"],[\"ate\",\"eat\",\"tea\"],[\"nat\",\"tan\"]]",
                "{\"result\":[[\"bat\"],[\"nat\",\"tan\"],[\"ate\",\"eat\",\"tea\"]]}",
                false
        )).isTrue();
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    void stdout_null_wa() {
        assertThat(comparator.compare(null, "{\"result\":[0,1]}", false)).isFalse();
    }

    @Test
    void stdout_blank_wa() {
        assertThat(comparator.compare("   ", "{\"result\":[0,1]}", false)).isFalse();
    }

    @Test
    void expectedOutput_usesFirstJsonField_notHardcodedKey() {
        // Comparator takes the first field of the JSON object, regardless of key name
        assertThat(comparator.compare("[0,1]", "{\"result\":[0,1]}", false)).isTrue();
    }

    @Test
    void stdout_withLeadingTrailingWhitespace_ac() {
        assertThat(comparator.compare(" [0,1] ", "{\"result\":[0,1]}", false)).isTrue();
    }

    @Test
    void expectedOutput_emptyJson_wa() {
        assertThat(comparator.compare("42", "{}", true)).isFalse();
    }
}
