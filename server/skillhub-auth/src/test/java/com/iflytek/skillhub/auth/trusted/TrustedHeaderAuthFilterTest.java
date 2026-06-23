package com.iflytek.skillhub.auth.trusted;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedHeaderAuthFilterTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final UserRoleBindingRepository roleBindingRepository = mock(UserRoleBindingRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private TrustedHeaderAuthProperties properties;
    private TrustedHeaderAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());

        properties = new TrustedHeaderAuthProperties();
        properties.setUserIdHeader("X-User-Id");
        properties.setDisplayNameHeader("X-User-Name");
        properties.setEmailHeader("X-User-Email");
        properties.setAutoProvision(true);

        filter = new TrustedHeaderAuthFilter(
                properties,
                userAccountRepository,
                roleBindingRepository,
                namespaceRepository,
                namespaceMemberRepository,
                transactionTemplate
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── helpers ──

    private UserAccount activeUser(String id, String displayName) {
        return new UserAccount(id, displayName, id + "@test.com", null);
    }

    private void stubUserFound(String userId, UserAccount user) {
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleBindingRepository.findByUserId(userId)).thenReturn(List.of());
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private PlatformPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof PlatformPrincipal p ? p : null;
    }

    // ════════════════════════════════════════════════════════════
    //  URL-DECODING
    // ════════════════════════════════════════════════════════════

    @Test
    void shouldAuthenticateUrlEncodedUserId() throws Exception {
        // %41%42%43 → "ABC"
        stubUserFound("ABC", activeUser("ABC", "Alice"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "%41%42%43");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().userId()).isEqualTo("ABC");
        assertThat(currentPrincipal().oauthProvider()).isEqualTo("trusted-header");
    }

    @Test
    void shouldStoreDecodedChineseDisplayName() throws Exception {
        // %E7%8E%8B%E6%8C%AF%E4%B8%AD → "王振中"
        UserAccount user = activeUser("alice", "old-name");
        stubUserFound("alice", user);
        when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "alice");
        request.addHeader("X-User-Name", "%E7%8E%8B%E6%8C%AF%E4%B8%AD");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().displayName()).isEqualTo("王振中");
    }

    @Test
    void shouldPassPlainTextUserIdUnchanged() throws Exception {
        stubUserFound("alice", activeUser("alice", "Alice"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "alice");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().userId()).isEqualTo("alice");
    }

    // ════════════════════════════════════════════════════════════
    //  FAIL-CLOSED: MALFORMED ENCODING
    // ════════════════════════════════════════════════════════════

    @Test
    void shouldRejectMalformedPercentEncoding() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "%ZZalice");

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectTruncatedPercentSequence() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "%4");

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ════════════════════════════════════════════════════════════
    //  CONTROL-CHAR REJECTION
    // ════════════════════════════════════════════════════════════

    @Test
    void shouldRejectNullByteInDecodedValue() throws Exception {
        // %00 → null byte
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "abc%00def");

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectNewlineInDecodedValue() throws Exception {
        // %0A → newline (control char)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "bob");
        request.addHeader("X-User-Name", "bob%0Asmith");

        // Even though userId is valid, the displayName control-char check in
        // syncUserInfo won't prevent auth from being set — the userId decode
        // succeeds first. But actually, decodeHeader is called for displayName
        // INSIDE syncUserInfo and returns null → displayName stays as-is from DB.
        // The auth is still set. So this test checks via userId with control char.
        // Let me use a control char in userId instead:
        request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "bob%0Asmith");

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ════════════════════════════════════════════════════════════
    //  NULL / EMPTY / BLANK HEADER
    // ════════════════════════════════════════════════════════════

    @Test
    void shouldSkipWhenUserIdHeaderIsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        // no X-User-Id header

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSkipWhenUserIdHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "   ");

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ════════════════════════════════════════════════════════════
    //  shouldNotFilter
    // ════════════════════════════════════════════════════════════

    @Nested
    class ShouldNotFilterTests {

        private MockHttpServletRequest request;

        @BeforeEach
        void setUp() {
            request = new MockHttpServletRequest();
        }

        @Test
        void shouldRunForApiV1Path() {
            request.setServletPath("/api/v1/skills");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        void shouldSkipForLoginPath() {
            request.setServletPath("/login");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        void shouldRunForApiWebWhenServletPathEmpty() {
            // servletPath defaults to "" → falls back to requestURI
            request.setRequestURI("/api/web/me/namespaces");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        void shouldSkipForActuatorWhenServletPathEmpty() {
            request.setRequestURI("/actuator/health");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        void shouldRunForApiV1Prefix() {
            request.setServletPath("/api/v1/skills/search");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        void shouldRunForApiWebPrefix() {
            request.setServletPath("/api/web/me/profile");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        void shouldRunForApiCliPrefix() {
            request.setServletPath("/api/cli/publish");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  AUTH-OVERRIDE (no getAuthentication()==null guard)
    // ════════════════════════════════════════════════════════════

    @Test
    void shouldOverrideExistingUsernamePasswordAuth() throws Exception {
        // Pre-seed a real UsernamePasswordAuthenticationToken
        var existingAuth = new UsernamePasswordAuthenticationToken(
                "other-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        stubUserFound("alice", activeUser("alice", "Alice"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "alice");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().userId()).isEqualTo("alice");
        assertThat(currentPrincipal().oauthProvider()).isEqualTo("trusted-header");
    }

    @Test
    void shouldOverrideExistingAnonymousAuth() throws Exception {
        // Pre-seed an AnonymousAuthenticationToken
        var anonAuth = new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonAuth);

        stubUserFound("alice", activeUser("alice", "Alice"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "alice");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().userId()).isEqualTo("alice");
        assertThat(currentPrincipal().oauthProvider()).isEqualTo("trusted-header");
    }

    @Test
    void shouldSetAuthWhenNoPriorAuth() throws Exception {
        // SecurityContext was created empty in @BeforeEach, no auth set
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        stubUserFound("alice", activeUser("alice", "Alice"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/whoami");
        request.addHeader("X-User-Id", "alice");

        runFilter(request);

        assertThat(currentPrincipal()).isNotNull();
        assertThat(currentPrincipal().userId()).isEqualTo("alice");
        assertThat(currentPrincipal().oauthProvider()).isEqualTo("trusted-header");
    }
}
