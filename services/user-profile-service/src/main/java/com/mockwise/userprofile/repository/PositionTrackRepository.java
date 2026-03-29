package com.mockwise.userprofile.repository;

import com.mockwise.userprofile.entity.PositionTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PositionTrackRepository extends JpaRepository<PositionTrack, String> {
    boolean existsByNameIgnoreCase(String name);
    List<PositionTrack> findAllByActiveTrue();
}
