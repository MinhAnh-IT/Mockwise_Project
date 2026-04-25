package com.interview.judge.codebuilder;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class CodeBuilder {

    private static final String PYTHON_USER_CODE_MARKER = "# === USER_CODE_INJECTED_HERE ===";

    @Value("classpath:drivers/UniversalJavaDriver.java")
    private Resource javaDriverResource;

    @Value("classpath:drivers/UniversalPythonDriver.py")
    private Resource pythonDriverResource;

    private String javaDriverCode;
    private String pythonDriverCode;

    @PostConstruct
    public void init() throws IOException {
        javaDriverCode   = javaDriverResource.getContentAsString(StandardCharsets.UTF_8);
        pythonDriverCode = pythonDriverResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("UniversalJavaDriver loaded ({} chars), UniversalPythonDriver loaded ({} chars)",
                javaDriverCode.length(), pythonDriverCode.length());
    }

    /**
     * Combines user solution code with the language-specific UniversalDriver
     * to produce a single source file ready for submission to Judge0.
     *
     * <p>Java result layout: imports, then {@code class Solution} (user code),
     * then {@code public class Main} (driver), then {@code TreeNode} /
     * {@code ListNode} struct definitions.
     *
     * <p>Python result layout (from the driver template, with the marker line
     * replaced by the user's {@code class Solution}): imports, {@code TreeNode}
     * and {@code ListNode} prelude, the user's Solution class, then driver
     * helpers and the {@code __main__} block.
     *
     * <p>For unsupported languages the user code is returned unchanged so the
     * caller can still send raw stdin/stdout solutions to Judge0.
     *
     * @param userCode raw user solution
     * @param language one of {@code "java"}, {@code "python"} (case-insensitive)
     * @return full source code string to send to Judge0
     */
    public String buildFullSource(String userCode, String language) {
        String lang = language == null ? "" : language.toLowerCase();
        return switch (lang) {
            case "java"          -> buildJavaSource(userCode);
            case "python", "py"  -> buildPythonSource(userCode);
            default              -> userCode;
        };
    }

    private String buildJavaSource(String userCode) {
        // Java allows only one public class per file (filename must match).
        // The driver's main class is Main, so Solution must NOT be public.
        String normalizedUserCode = userCode.replaceAll(
                "(?m)^(\\s*)public\\s+(class\\s+Solution\\b)", "$1$2");

        // Split driver into import section and class body section.
        // Imports must come before all class declarations in Java.
        String[] lines = javaDriverCode.split("\n");
        StringBuilder imports = new StringBuilder();
        StringBuilder body    = new StringBuilder();
        boolean pastImports   = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!pastImports && (trimmed.startsWith("import ") || trimmed.isEmpty())) {
                imports.append(line).append("\n");
            } else {
                pastImports = true;
                body.append(line).append("\n");
            }
        }

        return imports
                + "\n"
                + normalizedUserCode
                + "\n\n"
                + body;
    }

    private String buildPythonSource(String userCode) {
        if (!pythonDriverCode.contains(PYTHON_USER_CODE_MARKER)) {
            throw new IllegalStateException(
                    "Python driver template is missing the user-code marker: " + PYTHON_USER_CODE_MARKER);
        }
        // Marker is a literal string, not a regex — escape replacement for $ and \.
        return pythonDriverCode.replace(PYTHON_USER_CODE_MARKER, userCode);
    }
}
