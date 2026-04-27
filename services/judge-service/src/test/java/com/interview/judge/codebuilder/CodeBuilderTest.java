package com.interview.judge.codebuilder;

import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBuilderTest {

    private CodeBuilder codeBuilder;

    private static final String JAVA_DRIVER_STUB =
            "import java.util.*;\n" +
            "import java.util.regex.*;\n" +
            "\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {}\n" +
            "}\n";

    private static final String PYTHON_DRIVER_STUB =
            "import sys, json\n" +
            "\n" +
            "class TreeNode: pass\n" +
            "class ListNode: pass\n" +
            "\n" +
            "# === USER_CODE_INJECTED_HERE ===\n" +
            "\n" +
            "def _main():\n" +
            "    pass\n" +
            "\n" +
            "if __name__ == '__main__':\n" +
            "    _main()\n";

    private static final String JS_DRIVER_STUB =
            "\"use strict\";\n" +
            "\n" +
            "class TreeNode {}\n" +
            "class ListNode {}\n" +
            "\n" +
            "// === USER_CODE_INJECTED_HERE ===\n" +
            "\n" +
            "function _main() {}\n" +
            "_main();\n";

    private static final String CPP_DRIVER_STUB =
            "#include <bits/stdc++.h>\n" +
            "using namespace std;\n" +
            "\n" +
            "struct TreeNode {};\n" +
            "struct ListNode {};\n" +
            "\n" +
            "namespace _judge {}\n" +
            "\n" +
            "// === USER_CODE_INJECTED_HERE ===\n" +
            "\n" +
            "int main() {\n" +
            "    std::string _metaLine; std::getline(std::cin, _metaLine);\n" +
            "    // === DISPATCH_INJECTED_HERE ===\n" +
            "    return 0;\n" +
            "}\n";

    @BeforeEach
    void setUp() throws Exception {
        codeBuilder = new CodeBuilder();
        // Inject driver code directly (bypasses @PostConstruct / classpath loading)
        injectField("javaDriverCode",   JAVA_DRIVER_STUB);
        injectField("pythonDriverCode", PYTHON_DRIVER_STUB);
        injectField("jsDriverCode",     JS_DRIVER_STUB);
        injectField("cppDriverCode",    CPP_DRIVER_STUB);
    }

    private void injectField(String name, String value) throws Exception {
        Field field = CodeBuilder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(codeBuilder, value);
    }

    @Test
    void unsupportedLanguage_returnsUserCodeAsIs() {
        String code = "fn main() {}";
        assertThat(codeBuilder.buildFullSource(code, "rust")).isEqualTo(code);
    }

    @Test
    void java_importsComeBefore_solutionClass() {
        String userCode = "public class Solution {\n    public int[] twoSum() { return null; }\n}";
        String result = codeBuilder.buildFullSource(userCode, "java");

        int importIdx   = result.indexOf("import java.util.*");
        int solutionIdx = result.indexOf("class Solution");
        int mainIdx     = result.indexOf("class Main");

        assertThat(importIdx).isLessThan(solutionIdx);
        assertThat(solutionIdx).isLessThan(mainIdx);
    }

    @Test
    void java_stripsPublicFromSolutionClass() {
        String userCode = "public class Solution {\n    public int[] twoSum() { return null; }\n}";
        String result = codeBuilder.buildFullSource(userCode, "java");

        assertThat(result).doesNotContain("public class Solution");
        assertThat(result).contains("class Solution");
    }

    @Test
    void java_preservesPublicOnMainDriver() {
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "java");

        assertThat(result).contains("public class Main");
    }

    @Test
    void java_caseInsensitiveLanguage() {
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "JAVA");
        assertThat(result).contains("class Solution");
        assertThat(result).contains("public class Main");
    }

    // ── Python ────────────────────────────────────────────────────────────

    @Test
    void python_replacesMarkerWithUserCode() {
        String userCode = "class Solution:\n    def twoSum(self, nums, target):\n        return [0, 1]\n";
        String result = codeBuilder.buildFullSource(userCode, "python");

        assertThat(result).doesNotContain("# === USER_CODE_INJECTED_HERE ===");
        assertThat(result).contains("def twoSum(self, nums, target):");
    }

    @Test
    void python_preservesDriverPreludeAndMain() {
        String userCode = "class Solution: pass\n";
        String result = codeBuilder.buildFullSource(userCode, "python");

        // Prelude before user code
        int preludeIdx  = result.indexOf("class TreeNode");
        int solutionIdx = result.indexOf("class Solution: pass");
        int mainIdx     = result.indexOf("def _main()");

        assertThat(preludeIdx).isGreaterThan(-1);
        assertThat(solutionIdx).isGreaterThan(preludeIdx);
        assertThat(mainIdx).isGreaterThan(solutionIdx);
    }

    @Test
    void python_caseInsensitiveLanguage() {
        String userCode = "class Solution: pass";
        String result = codeBuilder.buildFullSource(userCode, "PYTHON");
        assertThat(result).contains("class Solution: pass");
    }

    @Test
    void python_pyAlias_alsoSupported() {
        String userCode = "class Solution: pass";
        String result = codeBuilder.buildFullSource(userCode, "py");
        assertThat(result).contains("class Solution: pass");
    }

    @Test
    void python_userCodeWithDollarSign_doesNotBreakReplacement() {
        // Regex-based replacers can mis-handle $ — verify literal replacement is used.
        String userCode = "class Solution:\n    def f(self): return '$1 ok'\n";
        String result = codeBuilder.buildFullSource(userCode, "python");
        assertThat(result).contains("return '$1 ok'");
    }

    // ── JavaScript ────────────────────────────────────────────────────────

    @Test
    void js_replacesMarkerWithUserCode() {
        String userCode = "class Solution {\n  twoSum(nums, target) { return [0, 1]; }\n}";
        String result = codeBuilder.buildFullSource(userCode, "javascript");

        assertThat(result).doesNotContain("// === USER_CODE_INJECTED_HERE ===");
        assertThat(result).contains("twoSum(nums, target) { return [0, 1]; }");
    }

    @Test
    void js_preservesDriverPreludeAndMain() {
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "javascript");

        int preludeIdx  = result.indexOf("class TreeNode");
        int solutionIdx = result.indexOf("class Solution {}");
        int mainIdx     = result.indexOf("function _main()");

        assertThat(preludeIdx).isGreaterThan(-1);
        assertThat(solutionIdx).isGreaterThan(preludeIdx);
        assertThat(mainIdx).isGreaterThan(solutionIdx);
    }

    @Test
    void js_caseInsensitiveLanguage() {
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "JAVASCRIPT");
        assertThat(result).contains("class Solution {}");
    }

    @Test
    void js_jsAlias_alsoSupported() {
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "js");
        assertThat(result).contains("class Solution {}");
    }

    @Test
    void js_userCodeWithDollarSign_doesNotBreakReplacement() {
        String userCode = "class Solution { f() { return '$1 ok'; } }";
        String result = codeBuilder.buildFullSource(userCode, "javascript");
        assertThat(result).contains("return '$1 ok'");
    }

    // ── C++ ───────────────────────────────────────────────────────────────

    private static FunctionMeta cppMeta(String fn, String returnType, boolean inPlace, ParamMeta... params) {
        return FunctionMeta.builder()
                .fn(fn)
                .returnType(returnType)
                .inPlace(inPlace)
                .orderMatters(true)
                .params(List.of(params))
                .build();
    }

    private static ParamMeta param(String name, String type) {
        return ParamMeta.builder().name(name).type(type).build();
    }

    @Test
    void cpp_replacesBothMarkersAndKeepsUserCode() {
        String userCode = "class Solution { public: int add(int a, int b) { return a + b; } };";
        FunctionMeta meta = cppMeta("add", "int", false, param("a", "int"), param("b", "int"));

        String result = codeBuilder.buildFullSource(userCode, "cpp", meta);

        assertThat(result).doesNotContain("// === USER_CODE_INJECTED_HERE ===");
        assertThat(result).doesNotContain("// === DISPATCH_INJECTED_HERE ===");
        assertThat(result).contains("int add(int a, int b)");
    }

    @Test
    void cpp_caseInsensitiveLanguage() {
        String userCode = "class Solution {};";
        FunctionMeta meta = cppMeta("f", "int", false);
        String result = codeBuilder.buildFullSource(userCode, "CPP", meta);
        assertThat(result).contains("class Solution");
    }

    @Test
    void cpp_cPlusPlusAlias_alsoSupported() {
        String userCode = "class Solution {};";
        FunctionMeta meta = cppMeta("f", "int", false);
        String result = codeBuilder.buildFullSource(userCode, "c++", meta);
        assertThat(result).contains("class Solution");
    }

    @Test
    void cpp_dispatchEmitsTypedDeclarations() {
        FunctionMeta meta = cppMeta("twoSum", "int[]", false,
                param("nums", "int[]"), param("target", "int"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("std::getline(std::cin, _line0)");
        assertThat(dispatch).contains("std::vector<int> nums = _judge::parseIntArray(_line0)");
        assertThat(dispatch).contains("int target = std::stoi(_line1)");
        assertThat(dispatch).contains("Solution sol;");
        assertThat(dispatch).contains("auto _result = sol.twoSum(nums, target);");
        assertThat(dispatch).contains("_judge::toJson(_result)");
    }

    @Test
    void cpp_dispatchInPlaceUsesFirstArgAsOutput() {
        FunctionMeta meta = cppMeta("sortColors", "void", true, param("nums", "int[]"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("sol.sortColors(nums);");
        assertThat(dispatch).doesNotContain("auto _result");
        assertThat(dispatch).contains("_judge::toJson(nums)");
    }

    @Test
    void cpp_dispatchTreeNodeUsesBuildTree() {
        FunctionMeta meta = cppMeta("maxDepth", "int", false, param("root", "TreeNode"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("TreeNode* root = _judge::buildTree(_line0)");
        assertThat(dispatch).contains("auto _result = sol.maxDepth(root);");
    }

    @Test
    void cpp_dispatchListNodeUsesBuildList() {
        FunctionMeta meta = cppMeta("reverseList", "ListNode", false, param("head", "ListNode"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("ListNode* head = _judge::buildList(_line0)");
        assertThat(dispatch).contains("auto _result = sol.reverseList(head);");
    }

    @Test
    void cpp_dispatchBoxedAliasesNormalize() {
        FunctionMeta meta = cppMeta("echo", "Integer", false, param("n", "Integer"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("int n = std::stoi(_line0)");
    }

    @Test
    void cpp_dispatchListGenericsMapToVector() {
        FunctionMeta meta = cppMeta("threeSum", "List<List<Integer>>", false,
                param("nums", "List<Integer>"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("std::vector<int> nums = _judge::parseIntArray(_line0)");
        // return type isn't part of dispatch directly — toJson(_result) handles it via overloads.
        assertThat(dispatch).contains("auto _result = sol.threeSum(nums);");
    }

    @Test
    void cpp_dispatchCharMatrixUsesParser() {
        FunctionMeta meta = cppMeta("exist", "boolean", false,
                param("board", "char[][]"), param("word", "String"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains(
                "std::vector<std::vector<char>> board = _judge::parseCharMatrix(_line0)");
        assertThat(dispatch).contains("std::string word = _line1;");
    }

    @Test
    void cpp_dispatchVoidWithoutInPlaceEmitsNullOutput() {
        FunctionMeta meta = cppMeta("noop", "void", false, param("x", "int"));

        String dispatch = codeBuilder.generateCppDispatch(meta);

        assertThat(dispatch).contains("sol.noop(x);");
        assertThat(dispatch).contains("\"null\"");
    }

    @Test
    void cpp_unsupportedTypeRaisesIllegalArgument() {
        FunctionMeta meta = cppMeta("f", "int", false, param("m", "Map<String, Integer>"));
        try {
            codeBuilder.generateCppDispatch(meta);
            assertThat(true).as("expected IllegalArgumentException").isFalse();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("Map<String, Integer>");
        }
    }

    @Test
    void cpp_nullFunctionMetaRaisesIllegalArgument() {
        try {
            codeBuilder.buildFullSource("class Solution {};", "cpp", null);
            assertThat(true).as("expected IllegalArgumentException").isFalse();
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("functionMeta");
        }
    }

    @Test
    void backwardCompat_twoArgOverloadStillWorksForJava() {
        // Java/Python/JS branches don't use functionMeta, so the legacy 2-arg
        // overload must keep working for callers that haven't migrated.
        String userCode = "class Solution {}";
        String result = codeBuilder.buildFullSource(userCode, "java");
        assertThat(result).contains("class Solution");
        assertThat(result).contains("public class Main");
    }
}
