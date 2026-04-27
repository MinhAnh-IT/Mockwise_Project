package com.interview.judge.codebuilder;

import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CodeBuilder {

    private static final String PYTHON_USER_CODE_MARKER = "# === USER_CODE_INJECTED_HERE ===";
    private static final String JS_USER_CODE_MARKER     = "// === USER_CODE_INJECTED_HERE ===";
    private static final String CPP_USER_CODE_MARKER    = "// === USER_CODE_INJECTED_HERE ===";
    private static final String CPP_DISPATCH_MARKER     = "// === DISPATCH_INJECTED_HERE ===";

    @Value("classpath:drivers/UniversalJavaDriver.java")
    private Resource javaDriverResource;

    @Value("classpath:drivers/UniversalPythonDriver.py")
    private Resource pythonDriverResource;

    @Value("classpath:drivers/UniversalJsDriver.js")
    private Resource jsDriverResource;

    @Value("classpath:drivers/UniversalCppDriver.cpp")
    private Resource cppDriverResource;

    private String javaDriverCode;
    private String pythonDriverCode;
    private String jsDriverCode;
    private String cppDriverCode;

    @PostConstruct
    public void init() throws IOException {
        javaDriverCode   = javaDriverResource.getContentAsString(StandardCharsets.UTF_8);
        pythonDriverCode = pythonDriverResource.getContentAsString(StandardCharsets.UTF_8);
        jsDriverCode     = jsDriverResource.getContentAsString(StandardCharsets.UTF_8);
        cppDriverCode    = cppDriverResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("Drivers loaded — java={} chars, python={} chars, js={} chars, cpp={} chars",
                javaDriverCode.length(), pythonDriverCode.length(),
                jsDriverCode.length(), cppDriverCode.length());
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
     * <p>JavaScript result layout (same template-replacement strategy as
     * Python): {@code TreeNode} / {@code ListNode} prelude, the user's
     * {@code class Solution}, then driver helpers and the {@code _main()}
     * invocation.
     *
     * <p>C++ result layout: prelude (TreeNode/ListNode), {@code _judge}
     * namespace with parsers and serializers, the user's {@code class Solution},
     * then a hand-rolled {@code main()} whose dispatch block is generated from
     * {@code functionMeta} (param parsing, call expression, output). C++
     * has no runtime reflection, so the dispatch must be baked in at codegen
     * time — that is why this overload requires {@code functionMeta}.
     *
     * <p>For unsupported languages the user code is returned unchanged so the
     * caller can still send raw stdin/stdout solutions to Judge0.
     *
     * @param userCode raw user solution
     * @param language one of {@code "java"}, {@code "python"}, {@code "javascript"}, {@code "cpp"} (case-insensitive)
     * @param functionMeta required for C++ codegen; may be {@code null} for the other languages
     * @return full source code string to send to Judge0
     */
    public String buildFullSource(String userCode, String language, FunctionMeta functionMeta) {
        String lang = language == null ? "" : language.toLowerCase();
        return switch (lang) {
            case "java"                  -> buildJavaSource(userCode);
            case "python", "py"          -> buildPythonSource(userCode);
            case "javascript", "js"      -> buildJsSource(userCode);
            case "cpp", "c++"            -> buildCppSource(userCode, functionMeta);
            default                      -> userCode;
        };
    }

    /** Backward-compatible overload. Use the 3-arg form for C++. */
    public String buildFullSource(String userCode, String language) {
        return buildFullSource(userCode, language, null);
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

    private String buildJsSource(String userCode) {
        if (!jsDriverCode.contains(JS_USER_CODE_MARKER)) {
            throw new IllegalStateException(
                    "JavaScript driver template is missing the user-code marker: " + JS_USER_CODE_MARKER);
        }
        return jsDriverCode.replace(JS_USER_CODE_MARKER, userCode);
    }

    private String buildCppSource(String userCode, FunctionMeta meta) {
        if (meta == null) {
            throw new IllegalArgumentException(
                    "C++ codegen requires functionMeta — use buildFullSource(code, lang, meta).");
        }
        if (!cppDriverCode.contains(CPP_USER_CODE_MARKER) ||
            !cppDriverCode.contains(CPP_DISPATCH_MARKER)) {
            throw new IllegalStateException(
                    "C++ driver template is missing one of its markers: " +
                    CPP_USER_CODE_MARKER + " / " + CPP_DISPATCH_MARKER);
        }
        String dispatch = generateCppDispatch(meta);
        return cppDriverCode
                .replace(CPP_USER_CODE_MARKER, userCode)
                .replace(CPP_DISPATCH_MARKER, dispatch);
    }

    /**
     * Generates the dispatch block for the C++ driver's {@code main()} —
     * one parsing block per param (typed declaration + parser call), then
     * the {@code Solution sol; sol.<fn>(...)} call, then output.
     *
     * <p>For {@code inPlace=true} the call's first argument is the output;
     * for void return the driver prints {@code "null"}; otherwise the
     * deduced {@code _result} is passed through {@code _judge::toJson}.
     */
    String generateCppDispatch(FunctionMeta meta) {
        StringBuilder sb = new StringBuilder();
        List<ParamMeta> params = meta.getParams();

        for (int i = 0; i < params.size(); i++) {
            String name = params.get(i).getName();
            String type = normalizeCppType(params.get(i).getType());
            sb.append("    std::string _line").append(i)
              .append("; std::getline(std::cin, _line").append(i).append(");\n");
            sb.append("    ").append(cppTypeFor(type)).append(" ").append(name)
              .append(" = ").append(cppParseExpr(type, "_line" + i)).append(";\n");
        }

        String callArgs = params.stream()
                .map(ParamMeta::getName)
                .collect(Collectors.joining(", "));

        sb.append("    Solution sol;\n");

        String returnType = normalizeCppType(meta.getReturnType());
        boolean inPlace = meta.isInPlace();

        if (inPlace) {
            sb.append("    sol.").append(meta.getFn()).append("(").append(callArgs).append(");\n");
            if (!params.isEmpty()) {
                sb.append("    std::cout << _judge::toJson(")
                  .append(params.get(0).getName())
                  .append(") << \"\\n\";\n");
            }
        } else if ("void".equals(returnType)) {
            sb.append("    sol.").append(meta.getFn()).append("(").append(callArgs).append(");\n");
            sb.append("    std::cout << \"null\" << \"\\n\";\n");
        } else {
            sb.append("    auto _result = sol.").append(meta.getFn())
              .append("(").append(callArgs).append(");\n");
            sb.append("    std::cout << _judge::toJson(_result) << \"\\n\";\n");
        }

        return sb.toString();
    }

    /**
     * Maps a schema type string to its C++ counterpart. Mirrors the Python /
     * JS driver's {@code _normalizeType} table, but emits raw C++ types.
     */
    private String cppTypeFor(String type) {
        return switch (type) {
            case "int"                  -> "int";
            case "long"                 -> "long long";
            case "double"               -> "double";
            case "boolean"              -> "bool";
            case "string"               -> "std::string";
            case "char"                 -> "char";
            case "int[]"                -> "std::vector<int>";
            case "long[]"               -> "std::vector<long long>";
            case "double[]"             -> "std::vector<double>";
            case "string[]"             -> "std::vector<std::string>";
            case "int[][]"              -> "std::vector<std::vector<int>>";
            case "char[][]"             -> "std::vector<std::vector<char>>";
            case "string[][]"           -> "std::vector<std::vector<std::string>>";
            case "List<Integer>"        -> "std::vector<int>";
            case "List<String>"         -> "std::vector<std::string>";
            case "List<List<Integer>>"  -> "std::vector<std::vector<int>>";
            case "List<List<String>>"   -> "std::vector<std::vector<std::string>>";
            case "TreeNode"             -> "TreeNode*";
            case "ListNode"             -> "ListNode*";
            default -> throw new IllegalArgumentException("Unsupported C++ type: " + type);
        };
    }

    /** Returns the C++ expression that parses a single stdin line into the given type. */
    private String cppParseExpr(String type, String lineVar) {
        return switch (type) {
            case "int"                  -> "std::stoi(" + lineVar + ")";
            case "long"                 -> "std::stoll(" + lineVar + ")";
            case "double"               -> "std::stod(" + lineVar + ")";
            case "boolean"              -> "(_judge::trim(" + lineVar + ") == \"true\")";
            case "string"               -> lineVar;
            case "char"                 -> "(" + lineVar + ".empty() ? '\\0' : " + lineVar + "[0])";
            case "int[]",
                 "List<Integer>"        -> "_judge::parseIntArray(" + lineVar + ")";
            case "long[]"               -> "_judge::parseLongArray(" + lineVar + ")";
            case "double[]"             -> "_judge::parseDoubleArray(" + lineVar + ")";
            case "string[]",
                 "List<String>"         -> "_judge::parseStringArray(" + lineVar + ")";
            case "int[][]",
                 "List<List<Integer>>"  -> "_judge::parseIntMatrix(" + lineVar + ")";
            case "char[][]"             -> "_judge::parseCharMatrix(" + lineVar + ")";
            case "string[][]",
                 "List<List<String>>"   -> "_judge::parseStringMatrix(" + lineVar + ")";
            case "TreeNode"             -> "_judge::buildTree(" + lineVar + ")";
            case "ListNode"             -> "_judge::buildList(" + lineVar + ")";
            default -> throw new IllegalArgumentException("Unsupported C++ type: " + type);
        };
    }

    /** Normalizes boxed / case-variant aliases to canonical schema names. */
    private String normalizeCppType(String type) {
        if (type == null) return "void";
        return switch (type) {
            case "String"      -> "string";
            case "String[]"    -> "string[]";
            case "String[][]"  -> "string[][]";
            case "Integer"     -> "int";
            case "Long"        -> "long";
            case "Double"      -> "double";
            case "Boolean"     -> "boolean";
            case "Character"   -> "char";
            default            -> type;
        };
    }
}
