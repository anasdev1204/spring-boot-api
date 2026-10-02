package com.footknow.api.common.security;

import com.footknow.api.common.error.ApiException;
import com.footknow.api.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentCaller {

    public String userId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        String subject = jwt.getToken().getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        return subject;
    }
}