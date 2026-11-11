package dev.codesentinel.auth;

import dev.codesentinel.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Principal get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof Principal p)) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        return p;
    }

    public static Principal instructor() {
        Principal p = get();
        if (!p.instructor()) throw ApiException.forbidden();
        return p;
    }
}
