package com.mockwise.iam.entity;

import com.mockwise.iam.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    @Id
    @UuidGenerator
    String userId;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String hashPass;

    @Column(nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    Role role = Role.User;

    @Column(nullable = false)
    @Builder.Default
    int tokenVersion = 1;

    @Column(nullable = false)
    @Builder.Default
    boolean isVerified = false;
}
