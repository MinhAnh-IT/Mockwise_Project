package com.mockwise.userprofile.repository;

import com.mockwise.userprofile.entity.Feedback;
import com.mockwise.userprofile.entity.FeedbackCategory;
import com.mockwise.userprofile.entity.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository
        extends JpaRepository<Feedback, String>, JpaSpecificationExecutor<Feedback> {

    long countByStatus(FeedbackStatus status);

    long countByCategory(FeedbackCategory category);

    /** Average rating across all feedback; null when there is none yet. */
    @Query("SELECT AVG(f.rating) FROM Feedback f")
    Double averageRating();
}
