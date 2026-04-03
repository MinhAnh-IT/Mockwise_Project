package com.interview.judge.codebuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBuilderTest {

    private CodeBuilder codeBuilder;

    private static final String DRIVER_STUB =
            "import java.util.*;\n" +
            "import java.util.regex.*;\n" +
            "\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {}\n" +
            "}\n";

    @BeforeEach
    void setUp() throws Exception {
        codeBuilder = new CodeBuilder();
        // Inject driver code directly (bypasses @PostConstruct / classpath loading)
        Field field = CodeBuilder.class.getDeclaredField("driverCode");
        field.setAccessible(true);
        field.set(codeBuilder, DRIVER_STUB);
    }

    @Test
    void nonJava_returnsUserCodeAsIs() {
        String code = "def solution(): pass";
        assertThat(codeBuilder.buildFullSource(code, "python")).isEqualTo(code);
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
}
