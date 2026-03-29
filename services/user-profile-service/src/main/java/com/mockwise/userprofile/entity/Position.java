package com.mockwise.userprofile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "positions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_position_track_level", columnNames = {"trackId", "levelId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

    @Id
    @UuidGenerator
    String positionId;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "trackId", nullable = false)
    PositionTrack track;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "levelId", nullable = false)
    PositionLevel level;
}
