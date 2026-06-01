package com.mehmetali.ledger.infrastructure;

import com.mehmetali.ledger.security.ApiKeyAuthFilter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    // In production: replace with LettuceBasedProxyManager (bucket4j-redis) for distributed rate limiting
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(ApiKeyAuthFilter.HEADER);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isWrite = isWriteMethod(request.getMethod());
        String bucketKey = apiKey + ":" + (isWrite ? "w" : "r");
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(isWrite));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                "{\"type\":\"/errors/rate-limit-exceeded\",\"status\":429,"
                    + "\"detail\":\"Rate limit exceeded. Retry after 60 seconds.\"}"
            );
        }
    }

    private Bucket buildBucket(boolean write) {
        int limit = write ? 60 : 300;
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)).build())
            .build();
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "DELETE".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);
    }
}
