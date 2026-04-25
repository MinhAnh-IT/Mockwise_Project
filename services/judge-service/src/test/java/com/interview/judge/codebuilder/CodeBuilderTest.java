package com.interview.judge.codebuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

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

    @BeforeEach
    void setUp() throws Exception {
        codeBuilder = new CodeBuilder();
        // Inject driver code directly (bypasses @PostConstruct / classpath loading)
        injectField("javaDriverCode",   JAVA_DRIVER_STUB);
        injectField("pythonDriverCode", PYTHON_DRIVER_STUB);
    }

    private void injectField(String name, String value) throws Exception {
        Field field = CodeBuilder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(codeBuilder, value);
    }

    @Test
    void unsupportedLanguage_returnsUserCodeAsIs() {
        String code = "function solve() {}";
        assertThat(codeBuilder.buildFullSource(code, "javascript")).isEqualTo(code);
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
}
