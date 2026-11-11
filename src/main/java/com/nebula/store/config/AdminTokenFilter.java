package com.nebula.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards {@code /api/admin/**} with a shared token.
 *
 * <p>The token is compared in constant time and never logged. A rejected request gets the
 * same RFC 7807 problem detail shape as every other API error.</p>
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);
    private static final URI UNAUTHORIZED_TYPE = URI.create("https://nebula.store/errors/unauthorized");

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(@Value("${app.admin-token}") String token, ObjectMapper objectMapper) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("app.admin-token must not be blank");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedToken)) {
            log.warn("Rejected admin request {} {} from {}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Передайте корректный заголовок " + HEADER);
            problem.setTitle("Требуется токен администратора");
            problem.setType(UNAUTHORIZED_TYPE);

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }
}
