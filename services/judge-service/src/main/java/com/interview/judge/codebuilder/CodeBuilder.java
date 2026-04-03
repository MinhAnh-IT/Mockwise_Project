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

    @Value("classpath:drivers/UniversalJavaDriver.java")
    private Resource driverResource;

    private String driverCode;

    @PostConstruct
    public void init() throws IOException {
        driverCode = driverResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("UniversalJavaDriver loaded ({} chars)", driverCode.length());
    }

    /**
     * Combines user solution code with the UniversalDriver to produce a single
     * Java source file ready for submission to Judge0.
     *
     * <p>Structure of resulting source:
     * <pre>
     * import java.util.*;
     * import com.fasterxml.jackson.databind.ObjectMapper;
     *
     * class Solution {
     *     // user code
     * }
     *
     * public class UniversalDriver { ... }
     * class TreeNode { ... }
     * class ListNode { ... }
     * </pre>
     *
     * @param userCode raw user solution (a {@code class Solution { ... }} block)
     * @param language submission language (must be "java")
     * @return full source code string to send to Judge0
     */
    public String buildFullSource(String userCode, String language) {
        if (!"java".equalsIgnoreCase(language)) {
            return userCode;
        }

        // Java allows only one public class per file (filename must match).
        // The driver's main class is UniversalDriver, so Solution must NOT be public.
        String normalizedUserCode = userCode.replaceAll(
                "(?m)^(\\s*)public\\s+(class\\s+Solution\\b)", "$1$2");

        // Split driver into import section and class body section.
        // Imports must come before all class declarations in Java.
        String[] lines = driverCode.split("\n");
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
}
