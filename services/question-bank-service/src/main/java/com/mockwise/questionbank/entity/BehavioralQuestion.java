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
    @Column(nullable = false, length = 64)
    Competency competency;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    String[] expectedSignals = new String[0];

    @Column(length = 500)
    String audioKey;

    /**
     * True if this question is suitable to open a session (warm-up, easy,
     * open-ended). The interview-service first-question algorithm prefers
     * openers and falls back to the rest of the pool only when none match.
     */
    @Column(name = "is_opener", nullable = false)
    @Builder.Default
    Boolean isOpener = false;
}
