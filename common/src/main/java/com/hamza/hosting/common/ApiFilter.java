package com.hamza.hosting.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiFilter extends OncePerRequestFilter {
    private final byte[] key;
    public ApiFilter(@Value("${api.key}") String key) { this.key = key.getBytes(StandardCharsets.UTF_8); }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String correlation = Trace.valid(req.getHeader(Trace.HEADER));
        res.setHeader(Trace.HEADER, correlation);
        try (var ignored = Trace.scope(correlation)) {
            String path = req.getRequestURI();
            boolean publicPath = path.startsWith("/actuator/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
            String actual = req.getHeader("X-API-Key");
            if (!publicPath && (key.length == 0 || actual == null || !MessageDigest.isEqual(key, actual.getBytes(StandardCharsets.UTF_8)))) {
                res.setStatus(401); res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Unauthorized\",\"correlationId\":\"" + correlation + "\"}"); return;
            }
            chain.doFilter(req, res);
        }
    }
}
