package com.mockwise.practice.common.security;

import com.mockwise.practice.common.exception.BusinessException;
import com.mockwise.practice.common.exception.StatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the authenticated user's id from the SecurityContext seeded by
 * {@link UserContextFilter}. The id always comes from the gateway-verified
 * token — controllers must never accept a userId from the request.
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** @throws BusinessException 401 when there is no authenticated user. */
    public static String requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details
                && details.getUserId() != null) {
            return details.getUserId();
        }
        throw new BusinessException(StatusCode.UNAUTHENTICATED);
    }
}
