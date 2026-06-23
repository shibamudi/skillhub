package com.iflytek.skillhub.auth.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared utilities for resolving the effective request path in the presence of
 * Spring's {@code ForwardedHeaderFilter}.
 *
 * <p>When {@code server.forward-headers-strategy=framework} is configured, Spring
 * registers a {@code ForwardedHeaderFilter} that rewrites
 * {@link HttpServletRequest#getRequestURI()} to include any {@code X-Forwarded-Prefix}
 * value. Under a sub-path deployment (e.g., behind a reverse proxy at {@code /skillhub}),
 * {@code getRequestURI()} returns {@code /skillhub/api/v1/...} instead of
 * {@code /api/v1/...}, causing path-prefix checks against route patterns to fail.
 *
 * <p>{@link HttpServletRequest#getServletPath()} is <em>not</em> affected by
 * {@code ForwardedHeaderFilter} and returns the path <em>after</em> prefix stripping,
 * which is what filters and matchers need for correct prefix matching.
 *
 * <p>This utility returns {@code getServletPath()} when it is non-null and non-empty,
 * falling back to {@code getRequestURI()} otherwise (e.g., outside a DispatcherServlet
 * context where {@code getServletPath()} may not be populated).
 */
public final class RequestPathUtils {

    private RequestPathUtils() {
    }

    /**
     * Returns the effective request path suitable for path-prefix matching.
     *
     * <p>Prefer {@code getServletPath()} over {@code getRequestURI()} to avoid
     * false negatives caused by {@code ForwardedHeaderFilter} prepending a
     * forwarded prefix to the URI.
     *
     * @param request the current HTTP request; must not be {@code null}
     * @return the path to use for prefix matching
     */
    public static String getForwardedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return (servletPath == null || servletPath.isEmpty()) ? request.getRequestURI() : servletPath;
    }
}
