package com.mockwise.questionbank.entity;

import com.mockwise.questionbank.enums.Competency;
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
@Table(name = "behavioral_questions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BehavioralQuestion {

    @Id
    String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    Question question;

    @Column(nullable = false, columnDefinition = "TEXT")
    String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    Competency competency;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    String[] expectedSignals = new String[0];

    @Column(length = 500)
    String audioKey;
}
