package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.QuestionFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionFollowUpRepository extends JpaRepository<QuestionFollowUp, String> {

    List<QuestionFollowUp> findByParentQuestionIdOrderByCreatedAtAsc(String parentQuestionId);

    Optional<QuestionFollowUp> findByParentQuestionIdAndProbesTargetKindAndProbesTargetValue(
            String parentQuestionId, String probesTargetKind, String probesTargetValue);
}
