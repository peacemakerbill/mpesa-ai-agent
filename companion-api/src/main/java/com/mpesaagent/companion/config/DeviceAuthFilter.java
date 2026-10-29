package com.mpesaagent.companion.config;

import com.mpesaagent.companion.service.LinkService;
import com.mpesaagent.companion.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/** Every /api/** call except signing in needs a valid token from a device that has not been signed out. */
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL = "devicePrincipal";

    private final TokenService tokens;
    private final LinkService links;

    public DeviceAuthFilter(TokenService tokens, LinkService links) {
        this.tokens = tokens;
        this.links = links;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals("/api/auth/verify") || path.equals("/api/auth/request-code");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        Optional<TokenService.Claims> claims = tokens.parse(token);
        if (claims.isEmpty() || !links.isActive(claims.get().phone(), claims.get().deviceId())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Please link this phone again\"}");
            return;
        }
        request.setAttribute(PRINCIPAL, new DevicePrincipal(claims.get().phone(), claims.get().deviceId()));
        chain.doFilter(request, response);
    }
}
