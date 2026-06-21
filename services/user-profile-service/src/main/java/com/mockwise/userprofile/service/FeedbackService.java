package com.mockwise.userprofile.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.dto.request.FeedbackCreateRequest;
import com.mockwise.userprofile.dto.request.FeedbackStatusUpdateRequest;
import com.mockwise.userprofile.dto.response.FeedbackResponse;
import com.mockwise.userprofile.dto.response.FeedbackStatsResponse;
import com.mockwise.userprofile.entity.Feedback;
import com.mockwise.userprofile.entity.FeedbackCategory;
import com.mockwise.userprofile.entity.FeedbackStatus;
import com.mockwise.userprofile.mapper.FeedbackMapper;
import com.mockwise.userprofile.repository.FeedbackRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackService {

    FeedbackRepository feedbackRepository;
    FeedbackMapper feedbackMapper;

    @Transactional
    public FeedbackResponse create(FeedbackCreateRequest request, String userId) {
        Feedback feedback = feedbackMapper.toEntity(request, userId);
        Feedback saved = feedbackRepository.save(feedback);
        log.info("Received feedback id={} category={} rating={} userId={}",
                saved.getId(), saved.getCategory(), saved.getRating(), userId);
        return feedbackMapper.toResponse(saved);
    }

    public Page<FeedbackResponse> search(
            FeedbackStatus status, FeedbackCategory category, Integer rating,
            String keyword, Pageable pageable) {
        Specification<Feedback> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("content")), like),
                        cb.like(cb.lower(root.get("contactEmail")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return feedbackRepository.findAll(spec, pageable).map(feedbackMapper::toResponse);
    }

    public FeedbackStatsResponse getStats() {
        Double avg = feedbackRepository.averageRating();
        return new FeedbackStatsResponse(
                feedbackRepository.count(),
                feedbackRepository.countByStatus(FeedbackStatus.NEW),
                feedbackRepository.countByStatus(FeedbackStatus.REVIEWED),
                feedbackRepository.countByStatus(FeedbackStatus.RESOLVED),
                feedbackRepository.countByCategory(FeedbackCategory.BUG),
                feedbackRepository.countByCategory(FeedbackCategory.FEATURE),
                feedbackRepository.countByCategory(FeedbackCategory.GENERAL),
                avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0);
    }

    @Transactional
    public FeedbackResponse updateStatus(String id, FeedbackStatusUpdateRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Feedback not found: " + id));

        feedback.setStatus(request.status());
        if (request.adminNote() != null) {
            feedback.setAdminNote(request.adminNote());
        }
        // Stamp the first time it leaves NEW so we keep the original review time.
        if (request.status() != FeedbackStatus.NEW && feedback.getReviewedAt() == null) {
            feedback.setReviewedAt(Instant.now());
        }

        Feedback saved = feedbackRepository.save(feedback);
        log.info("Updated feedback id={} -> status={}", id, saved.getStatus());
        return feedbackMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        if (!feedbackRepository.existsById(id)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "Feedback not found: " + id);
        }
        feedbackRepository.deleteById(id);
        log.info("Deleted feedback id={}", id);
    }
}
