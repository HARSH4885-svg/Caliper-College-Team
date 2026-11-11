package dev.codesentinel.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Per-IP limit on the abusable endpoints: login, register and submission upload (NFR-SEC, deployment blocker B4). */
@Component
@ConditionalOnWebApplication
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;

    public RateLimitFilter(AppProperties props) {
        this.limiter = new RateLimiter(props.rateLimitPerMinute(), 60_000);
    }

    static boolean limited(String method, String uri) {
        return "POST".equals(method) && (uri.equals("/api/auth/login") || uri.equals("/api/auth/register") || uri.equals("/api/auth/password") || uri.equals("/api/courses/join")
                || (uri.startsWith("/api/assignments/") && uri.endsWith("/submissions")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (limited(req.getMethod(), req.getRequestURI()) && !limiter.allow(req.getRemoteAddr() + (req.getRequestURI().contains("/auth/") ? "|auth" : "|upload"), System.currentTimeMillis())) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Too many requests. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
