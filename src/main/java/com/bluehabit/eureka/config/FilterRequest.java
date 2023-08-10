/*
 * Copyright © 2023 Blue Habit.
 *
 * Unauthorized copying, publishing of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.bluehabit.eureka.config;

import com.bluehabit.eureka.common.JwtUtil;
import com.bluehabit.eureka.exception.UnAuthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;

@Component
public class FilterRequest extends OncePerRequestFilter {
    private static final int LENGTH_BEARER = 6;

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userService;

    private List<String> allowList = List.of(
        "/api/v1/auth/sign-in-email",
        "/api/v1/auth/sign-in-google",
        "/api/v1/auth/sign-up-email",
        "/api/v1/auth/otp-confirmation",
        "/api/v1/auth/complete-profile",
        "/api/v1/auth/request-reset-password",
        "/api/v1/auth/link-confirmation",
        "/api/v1/auth/reset-password",
        "/api/v1/auth/refresh-token"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {

            if (new AntPathMatcher().match("/api/v1/auth/**", request.getServletPath())) {
                filterChain.doFilter(request, response);
                return;
            }

            final String authHeader = request.getHeader("Authorization");
            if (authHeader == null || authHeader.isEmpty()) {
                throw new UnAuthorizedException("header empty");
            }

            if (!authHeader.startsWith("Bearer")) {
                throw new UnAuthorizedException("header doesn't contain Bearer");
            }

            if (authHeader.length() <= LENGTH_BEARER) {
                throw new UnAuthorizedException("Header only contains 'Bearer'");
            }

            final String token = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(token);

            if (username.isEmpty()) {
                throw new UnAuthorizedException("failed extract claim");
            }

            final UserDetails userDetails = userService.loadUserByUsername(username);
            if (!jwtUtil.validateToken(token, userDetails)) {
                throw new UnAuthorizedException("user not found");
            }

            final UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            filterChain.doFilter(request, response);
        } catch (UnAuthorizedException exception) {
            resolver.resolveException(request, response, null, exception);
        }

    }
}
