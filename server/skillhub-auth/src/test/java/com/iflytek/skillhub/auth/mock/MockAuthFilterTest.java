package com.iflytek.skillhub.auth.mock;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockAuthFilterTest {

    private final UserAccountRepository userRepo = mock(UserAccountRepository.class);
    private final UserRoleBindingRepository roleBindingRepo = mock(UserRoleBindingRepository.class);
    private final PlatformSessionService platformSessionService = mock(PlatformSessionService.class);

    private final MockAuthFilter filter = new MockAuthFilter(userRepo, roleBindingRepo, platformSessionService);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserAccount activeUser(String id) {
        return new UserAccount(id, "Test User", id + "@test.com", null);
    }

    private void stubUserFound(String userId) {
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(roleBindingRepo.findByUserId(userId)).thenReturn(List.of());
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void shouldSetAuthWhenNoPriorAuthAndHeaderPresent() throws Exception {
        stubUserFound("local-user");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Mock-User-Id", "local-user");

        runFilter(request);

        ArgumentCaptor<PlatformPrincipal> captor = ArgumentCaptor.forClass(PlatformPrincipal.class);
        verify(platformSessionService).establishSession(captor.capture(), eq(request), eq(false));

        PlatformPrincipal principal = captor.getValue();
        assertThat(principal.userId()).isEqualTo("local-user");
        assertThat(principal.oauthProvider()).isEqualTo("mock");
        assertThat(principal.platformRoles()).contains("USER");
    }

    @Test
    void shouldOverrideAnonymousAuthWhenHeaderPresent() throws Exception {
        // Pre-seed anonymous auth
        var anonAuth = new AnonymousAuthenticationToken(
                "anon-key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonAuth);

        stubUserFound("local-admin");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Mock-User-Id", "local-admin");

        runFilter(request);

        // Verify establishSession was called (overrides anonymous)
        ArgumentCaptor<PlatformPrincipal> captor = ArgumentCaptor.forClass(PlatformPrincipal.class);
        verify(platformSessionService).establishSession(captor.capture(), eq(request), eq(false));
        assertThat(captor.getValue().userId()).isEqualTo("local-admin");
    }

    @Test
    void shouldNotOverrideRealAuthWhenHeaderPresent() throws Exception {
        // Pre-seed a real UsernamePasswordAuthenticationToken
        var realAuth = new UsernamePasswordAuthenticationToken(
                "real-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(realAuth);

        stubUserFound("local-user");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Mock-User-Id", "local-user");

        runFilter(request);

        // establishSession should NOT be called — real auth is preserved
        verify(platformSessionService, never()).establishSession(any(), any(), anyBoolean());

        // SecurityContext still has original auth
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(realAuth);
    }

    @Test
    void shouldDoNothingWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // no X-Mock-User-Id header

        runFilter(request);

        verify(platformSessionService, never()).establishSession(any(), any(), anyBoolean());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
