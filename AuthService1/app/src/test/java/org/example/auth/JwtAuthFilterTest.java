package org.example.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.service.CustomUserDetails;
import org.example.service.JwtService;
import org.example.service.UserDetailsServiceImpl;
import org.example.entities.UserInfo;
import org.example.entities.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CustomUserDetails getUser() {
        UserInfo user = UserInfo.builder()
                .userId("1")
                .username("john")
                .password("password")
                .roles(Set.of(UserRole.ROLE_USER))
                .build();

        return new CustomUserDetails(user);
    }

    //---------------------------------------------------------
    // Missing Authorization header
    //---------------------------------------------------------

    @Test
    void shouldContinueWhenAuthorizationHeaderMissing() throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn(null);

        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    //---------------------------------------------------------
    // Header not Bearer
    //---------------------------------------------------------

    @Test
    void shouldContinueWhenHeaderIsNotBearer() throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Basic abc");

        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    //---------------------------------------------------------
    // Valid JWT
    //---------------------------------------------------------

    @Test
    void shouldAuthenticateUserWhenTokenIsValid() throws Exception {

        CustomUserDetails user = getUser();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractUsername("token"))
                .thenReturn("john");

        when(jwtService.extractUserId("token"))
                .thenReturn("1");

        when(userDetailsService.loadUserByUsername("john"))
                .thenReturn(user);

        when(jwtService.validateToken("token", user))
                .thenReturn(true);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        assertEquals(
                "1",
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal());

        verify(filterChain).doFilter(request, response);
    }

    //---------------------------------------------------------
    // Invalid JWT
    //---------------------------------------------------------

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid() throws Exception {

        CustomUserDetails user = getUser();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractUsername("token"))
                .thenReturn("john");

        when(jwtService.extractUserId("token"))
                .thenReturn("1");

        when(userDetailsService.loadUserByUsername("john"))
                .thenReturn(user);

        when(jwtService.validateToken("token", user))
                .thenReturn(false);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(filterChain).doFilter(request, response);
    }

    //---------------------------------------------------------
    // Username null
    //---------------------------------------------------------

    @Test
    void shouldNotAuthenticateWhenUsernameIsNull() throws Exception {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractUsername("token"))
                .thenReturn(null);

        when(jwtService.extractUserId("token"))
                .thenReturn("1");

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verify(userDetailsService, never())
                .loadUserByUsername(any());

        verify(filterChain).doFilter(request, response);
    }

    //---------------------------------------------------------
    // Already authenticated
    //---------------------------------------------------------

    @Test
    void shouldSkipAuthenticationWhenAlreadyAuthenticated() throws Exception {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "1",
                        null,
                        null);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractUsername("token"))
                .thenReturn("john");

        when(jwtService.extractUserId("token"))
                .thenReturn("1");

        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(userDetailsService, never())
                .loadUserByUsername(any());

        verify(filterChain).doFilter(request, response);
    }

    //---------------------------------------------------------
    // Exception while parsing JWT
    //---------------------------------------------------------

    @Test
    void shouldContinueFilterWhenExceptionOccurs() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtService.extractUsername("token")).thenThrow(new RuntimeException("Bad token"));
        jwtAuthFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    //---------------------------------------------------------
    // Validate token throws exception
    //---------------------------------------------------------

    @Test
    void shouldContinueWhenValidateTokenThrowsException() throws Exception {

        CustomUserDetails user = getUser();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer token");

        when(jwtService.extractUsername("token"))
                .thenReturn("john");

        when(jwtService.extractUserId("token"))
                .thenReturn("1");

        when(userDetailsService.loadUserByUsername("john"))
                .thenReturn(user);

        when(jwtService.validateToken("token", user))
                .thenThrow(new RuntimeException("JWT Error"));

        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}