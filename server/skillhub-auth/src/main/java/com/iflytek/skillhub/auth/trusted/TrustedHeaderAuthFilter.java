package com.iflytek.skillhub.auth.trusted;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication filter for trusted header mode.
 *
 * <p>When enabled, this filter extracts user identity from HTTP headers set by a
 * trusted reverse proxy (e.g., Traefik ForwardAuth). It operates in stateless mode
 * like ApiTokenAuthenticationFilter - no session is created.
 *
 * <p>If the user doesn't exist, it can auto-provision a new user account.
 */
@Component
@ConditionalOnProperty(name = "skillhub.auth.trusted-header.enabled", havingValue = "true")
@Order(-100)
public class TrustedHeaderAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrustedHeaderAuthFilter.class);

    private final TrustedHeaderAuthProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final TransactionTemplate transactionTemplate;

    public TrustedHeaderAuthFilter(TrustedHeaderAuthProperties properties,
                                   UserAccountRepository userAccountRepository,
                                   RoleRepository roleRepository,
                                   UserRoleBindingRepository userRoleBindingRepository,
                                   NamespaceRepository namespaceRepository,
                                   NamespaceMemberRepository namespaceMemberRepository,
                                   TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(properties.getUserIdHeader());

        if (userId != null && !userId.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Find or auto-provision user (with retry for race condition)
                UserAccount user = findOrProvisionUser(userId, request);

                // Sync user info from headers
                syncUserInfo(user, request);

                // Load roles from DB (consistent with MockAuthFilter and ApiTokenAuthenticationFilter)
                Set<String> roles = userRoleBindingRepository.findByUserId(userId).stream()
                        .map(rb -> rb.getRole().getCode())
                        .collect(Collectors.toSet());
                roles = PlatformRoleDefaults.withDefaultUserRole(roles);

                PlatformPrincipal principal = new PlatformPrincipal(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getAvatarUrl(),
                        "trusted-header",
                        roles
                );

                // Set authentication (stateless, no session)
                var authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Trusted header auth successful for user: {}", userId);
            } catch (Exception e) {
                log.error("Trusted header auth failed for user: {}", userId, e);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Find existing user or auto-provision a new one.
     *
     * <p>If two requests arrive simultaneously for the same new userId, both may miss
     * findById and both attempt autoProvisionUser. The second will hit a PK constraint
     * violation, which is caught by the outer try-catch in doFilterInternal. The user
     * will get a 401 and should retry the request.
     */
    private UserAccount findOrProvisionUser(String userId, HttpServletRequest request) {
        return userAccountRepository.findById(userId)
                .orElseGet(() -> autoProvisionUser(userId, request));
    }

    /**
     * Auto-provision a new user account with role and namespace membership.
     *
     * <p>Runs in a transaction to ensure atomicity — if any write fails, all are rolled back,
     * preventing zombie users (account without roles or namespace membership).
     */
    private UserAccount autoProvisionUser(String userId, HttpServletRequest request) {
        if (!properties.isAutoProvision()) {
            throw new IllegalStateException("Auto-provisioning is disabled, user not found: " + userId);
        }

        String email = request.getHeader(properties.getEmailHeader());
        String displayName = request.getHeader(properties.getDisplayNameHeader());

        if (displayName == null || displayName.isBlank()) {
            displayName = userId;
        }

        final String finalEmail = email;
        final String finalDisplayName = displayName;

        return transactionTemplate.execute(status -> {
            // Create user account
            UserAccount user = new UserAccount(userId, finalDisplayName, finalEmail, null);
            user = userAccountRepository.save(user);

            // Assign default USER role
            Role defaultRole = roleRepository.findByCode("USER")
                    .orElseThrow(() -> new IllegalStateException("Missing built-in role: USER"));
            userRoleBindingRepository.save(new UserRoleBinding(userId, defaultRole));

            // Add to global namespace
            Namespace globalNs = namespaceRepository.findBySlug("global")
                    .orElseThrow(() -> new IllegalStateException("Missing built-in namespace: global"));
            namespaceMemberRepository.save(new NamespaceMember(globalNs.getId(), userId, NamespaceRole.MEMBER));

            log.info("Auto-provisioned user from trusted header: {}", userId);
            return user;
        });
    }

    private void syncUserInfo(UserAccount user, HttpServletRequest request) {
        String email = request.getHeader(properties.getEmailHeader());
        String displayName = request.getHeader(properties.getDisplayNameHeader());

        boolean changed = false;

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        if (displayName != null && !displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            changed = true;
        }

        if (changed) {
            userAccountRepository.save(user);
            log.debug("Synced user info from trusted header: {}", user.getId());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/")
            || path.startsWith("/api/web/")
            || path.startsWith("/api/cli/"));
    }
}
