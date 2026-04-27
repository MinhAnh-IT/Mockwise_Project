package com.mockwise.ttsstt.stt.service;

import com.mockwise.ttsstt.stt.entity.TranscriptWord;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScribeResult {
    String text;
    String languageCode;
    Float languageConfidence;
    List<TranscriptWord> words;
}
