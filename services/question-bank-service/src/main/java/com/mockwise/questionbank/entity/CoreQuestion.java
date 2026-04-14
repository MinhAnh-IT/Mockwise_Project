package com.mockwise.questionbank.entity;

import com.mockwise.questionbank.enums.Domain;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "core_questions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CoreQuestion {

    @Id
    String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    Question question;

    @Column(nullable = false, columnDefinition = "TEXT")
    String text;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    String[] targetRoles = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    Domain domain;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    String[] keyConcepts = new String[0];

    @Column(nullable = false, columnDefinition = "TEXT")
    String depthExpected;

    @Column(length = 500)
    String audioKey;
}
