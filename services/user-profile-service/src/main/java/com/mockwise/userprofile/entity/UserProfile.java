package com.mockwise.userprofile.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

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
}
