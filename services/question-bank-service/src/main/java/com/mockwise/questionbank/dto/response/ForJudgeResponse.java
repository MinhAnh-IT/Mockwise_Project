package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.entity.FunctionMeta;
import com.mockwise.questionbank.entity.TestCase;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForJudgeResponse {

    String language;
    FunctionMeta functionMeta;
    List<JudgeTestCase> testCases;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class JudgeTestCase {
        String id;
        Map<String, Object> inputData;
        Map<String, Object> expectedOutput;

        public static JudgeTestCase from(TestCase tc) {
            JudgeTestCase jt = new JudgeTestCase();
            jt.setId(tc.getId());
            jt.setInputData(tc.getInputData());
            jt.setExpectedOutput(tc.getExpectedOutput());
            return jt;
        }
    }
}
