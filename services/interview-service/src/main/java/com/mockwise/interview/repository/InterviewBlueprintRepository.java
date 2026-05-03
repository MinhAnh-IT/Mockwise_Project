package com.mockwise.interview.repository;

import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.enums.InterviewType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterviewBlueprintRepository extends JpaRepository<InterviewBlueprint, UUID> {

    /**
     * Loads the live blueprint for a given (role, level, type). The schema's
     * partial unique index guarantees at most one default per tuple, so the
     * Optional is genuinely 0-or-1.
     */
    Optional<InterviewBlueprint> findFirstByTargetRoleAndLevelAndInterviewTypeAndIsDefaultTrue(
            String targetRole, String level, InterviewType interviewType);
}
