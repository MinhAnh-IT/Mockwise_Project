package com.mockwise.questionbank.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "coding_questions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CodingQuestion {

    @Id
    String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    Question question;

    @Column(nullable = false, length = 255)
    String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    String description;


    @Column(columnDefinition = "TEXT")
    String constraints;

    @Column(nullable = false, length = 50)
    String optimalTimeComplexity;

    @Column(nullable = false, length = 50)
    String optimalSpaceComplexity;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    FunctionMeta functionMeta;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    StarterCode starterCode;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    List<TestCase> testCases = new ArrayList<>();
}
