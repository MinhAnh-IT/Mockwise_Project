package com.mockwise.userprofile.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "position_levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 64)
    String positionRole;

    @Builder.Default
    @Column(nullable = false)
    Boolean active = true;
}
