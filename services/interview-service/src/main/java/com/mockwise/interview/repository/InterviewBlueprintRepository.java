package com.mockwise.interview.repository;

import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.enums.InterviewType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InterviewBlueprintRepository
        extends JpaRepository<InterviewBlueprint, UUID>, JpaSpecificationExecutor<InterviewBlueprint> {

    /**
     * Loads the live blueprint for a given (role, level, type). The schema's
     * partial unique index guarantees at most one default per tuple, so the
     * Optional is genuinely 0-or-1.
     */
    Optional<InterviewBlueprint> findFirstByTargetRoleAndLevelAndInterviewTypeAndIsDefaultTrue(
            String targetRole, String level, InterviewType interviewType);

    /**
     * Clears is_default on every blueprint in the same (role, level, type)
     * tuple except {@code id}. Run inside the same transaction as the
     * promote-to-default write so the partial unique index never sees two
     * defaults at once.
     */
    @Modifying
    @Query("update InterviewBlueprint b set b.isDefault = false " +
           "where b.targetRole = :role and b.level = :level " +
           "and b.interviewType = :type and b.id <> :id and b.isDefault = true")
    int clearOtherDefaults(@Param("role") String role,
                           @Param("level") String level,
                           @Param("type") InterviewType type,
                           @Param("id") UUID id);
}
