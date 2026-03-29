package com.mockwise.userprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "position_tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionTrack {

    @Id
    @UuidGenerator
    String id;

    @Column(nullable = false)
    String name;

    @Builder.Default
    @Column(nullable = false)
    Boolean active = true;
}
