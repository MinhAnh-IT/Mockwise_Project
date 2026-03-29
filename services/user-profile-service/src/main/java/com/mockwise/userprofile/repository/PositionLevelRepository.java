package com.mockwise.userprofile.repository;

import com.mockwise.userprofile.entity.PositionLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PositionLevelRepository extends JpaRepository<PositionLevel, String> {
    boolean existsByPositionRoleIgnoreCase(String positionRole);
    List<PositionLevel> findAllByActiveTrue();
}
