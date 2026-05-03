package com.mockwise.userprofile.entity;

import com.mockwise.userprofile.entity.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile {
    @Id
    String userId;

    @Column(nullable = false)
    String fullName;

    @Column(nullable = false)
    String city;

    @Column(nullable = false)
    Integer experience;

    /**
     * Latest avatar object key in storage-service. Null = no avatar uploaded yet
     * (UI falls back to the user's initials).
     */
    @Column(name = "avatar_object_key", length = 500)
    String avatarObjectKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "positionId", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_profile_position"))
    Position position;

    /**
     * Tools / languages / frameworks user works with — normalized lowercase tokens
     * (e.g. ["java", "spring", "postgresql"]). Used by interview-service to
     * filter coding question starter languages and bias core question selection.
     */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tech_stack", columnDefinition = "JSON")
    List<String> techStack = new ArrayList<>();

    /**
     * Preferred language for question audio (TTS) and AI feedback. Defaults to
     * Vietnamese to match the platform default.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", length = 8)
    Language preferredLanguage = Language.VI;

    /**
     * Years in the user's current track/level. May be smaller than {@link #experience}
     * if the user recently switched roles. Used to refine difficulty calibration:
     * a senior with only 6 months in a new track gets eased difficulty.
     */
    @Column(name = "years_in_current_role")
    Integer yearsInCurrentRole;

    /**
     * Industries the user has worked in (e.g. ["fintech", "ecommerce"]). Used to
     * bias behavioral question selection toward relevant scenarios.
     */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "industries", columnDefinition = "JSON")
    List<String> industries = new ArrayList<>();
}
