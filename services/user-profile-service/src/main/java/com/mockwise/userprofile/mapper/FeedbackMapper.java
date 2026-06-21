package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.request.FeedbackCreateRequest;
import com.mockwise.userprofile.dto.response.FeedbackResponse;
import com.mockwise.userprofile.entity.Feedback;
import org.springframework.stereotype.Component;

/**
 * Plain mapper for Feedback. Kept manual (rather than MapStruct) because the
 * entity ↔ response mapping is a flat 1:1 copy and the submit path needs to
 * stamp the optional {@code userId} from the request context.
 */
@Component
public class FeedbackMapper {

    public Feedback toEntity(FeedbackCreateRequest request, String userId) {
        return Feedback.builder()
                .userId(userId)
                .rating(request.rating())
                .category(request.category())
                .content(request.content())
                .contactEmail(normalizeEmail(request.contactEmail()))
                .build();
    }

    public FeedbackResponse toResponse(Feedback entity) {
        return new FeedbackResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getRating(),
                entity.getCategory(),
                entity.getContent(),
                entity.getContactEmail(),
                entity.getStatus(),
                entity.getAdminNote(),
                entity.getCreatedAt(),
                entity.getReviewedAt());
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
