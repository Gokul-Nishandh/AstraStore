/**
 * The guard rails that keep a deployment administrable.
 *
 * <p>Each of the three admin operations has a way to reach a state with no
 * administrator, and each is closed here rather than in the controller: demote
 * the last one, disable the last one, delete the last one. There is no
 * supported route back from that state — it takes a hand-edited database — so
 * these tests exist to make the guards hard to remove by accident.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.dto.AdminUserResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.entity.UserDeletionEvent;
import com.astrastore.auth.exception.ConflictException;
import com.astrastore.auth.exception.ForbiddenException;
import com.astrastore.auth.repository.ApiKeyRepository;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LastAdminProtectionTest {

    private static final long ACTOR_ID = 1L;
    private static final long OTHER_ADMIN_ID = 2L;

    private UserRepository userRepository;
    private ApiKeyRepository apiKeyRepository;
    private RefreshTokenService refreshTokenService;
    private AccountDeletionService accountDeletionService;
    private AuditLogService auditLogService;
    private UserAdminService service;

    private MockHttpServletRequest request;
    private User actor;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        apiKeyRepository = mock(ApiKeyRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        accountDeletionService = mock(AccountDeletionService.class);
        auditLogService = mock(AuditLogService.class);
        service = new UserAdminService(userRepository, apiKeyRepository,
                refreshTokenService, accountDeletionService, auditLogService);

        request = new MockHttpServletRequest();
        actor = admin(ACTOR_ID);

        when(apiKeyRepository.findByUserIdAndRevokedFalse(anyLong())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    // --- demotion ------------------------------------------------------------

    @Test
    @DisplayName("an admin cannot strip their own ADMIN role")
    void adminCannotDemoteThemselves() {
        given(actor);
        // Two admins exist, so this is refused for being self-demotion rather
        // than for being the last one.
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(2L);

        assertThatThrownBy(() -> service.updateRoles(actor, ACTOR_ID, Set.of(Roles.USER), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own ADMIN role");

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService).record(eq(ACTOR_ID), any(), eq(AuditAction.ROLE_CHANGE_DENIED),
                any(), eq(false), any(), any());
    }

    @Test
    @DisplayName("the last admin cannot be demoted by another actor either")
    void lastAdminCannotBeDemoted() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateRoles(actor, OTHER_ADMIN_ID, Set.of(Roles.USER), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last administrator");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("demotion goes through once a second admin exists")
    void demotionIsAllowedWhileAnotherAdminRemains() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(2L);

        AdminUserResponse response =
                service.updateRoles(actor, OTHER_ADMIN_ID, Set.of(Roles.USER), request);

        assertThat(response.roles()).containsExactly(Roles.USER);
        // Roles are baked into access tokens, so the demoted user's sessions
        // must not survive to mint one carrying the role they just lost.
        verify(refreshTokenService).revokeAllForUser(OTHER_ADMIN_ID);
    }

    @Test
    @DisplayName("granting ADMIN to the last admin again is not mistaken for a demotion")
    void reassertingAdminIsNotBlocked() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(1L);

        AdminUserResponse response = service.updateRoles(
                actor, OTHER_ADMIN_ID, new LinkedHashSet<>(List.of(Roles.ADMIN, Roles.USER)), request);

        assertThat(response.roles()).contains(Roles.ADMIN);
    }

    // --- disabling ------------------------------------------------------------

    @Test
    @DisplayName("an admin cannot disable their own account")
    void adminCannotDisableThemselves() {
        given(actor);

        assertThatThrownBy(() -> service.updateStatus(actor, ACTOR_ID, false, null, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("the last enabled admin cannot be disabled")
    void lastEnabledAdminCannotBeDisabled() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countEnabledByRole(Roles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateStatus(actor, OTHER_ADMIN_ID, false, "offboarding", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last enabled administrator");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("disabling is allowed while a second enabled admin remains")
    void disablingIsAllowedWhileAnotherEnabledAdminRemains() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countEnabledByRole(Roles.ADMIN)).thenReturn(2L);

        AdminUserResponse response =
                service.updateStatus(actor, OTHER_ADMIN_ID, false, "offboarding", request);

        assertThat(response.enabled()).isFalse();
        verify(refreshTokenService).revokeAllForUser(OTHER_ADMIN_ID);
    }

    // --- deletion --------------------------------------------------------------

    @Test
    @DisplayName("the last admin cannot be deleted")
    void lastAdminCannotBeDeleted() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(actor, OTHER_ADMIN_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last administrator");

        verify(accountDeletionService, never()).deleteAccount(any(), any(), any());
    }

    @Test
    @DisplayName("self-deletion through the admin endpoint is redirected to the self-service one")
    void adminCannotDeleteThemselvesHere() {
        given(actor);

        assertThatThrownBy(() -> service.deleteUser(actor, ACTOR_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("/api/auth/account");

        verify(accountDeletionService, never()).deleteAccount(any(), any(), any());
    }

    @Test
    @DisplayName("deleting an admin is allowed while another remains")
    void deletionIsAllowedWhileAnotherAdminRemains() {
        User target = admin(OTHER_ADMIN_ID);
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(2L);

        service.deleteUser(actor, OTHER_ADMIN_ID, request);

        verify(accountDeletionService).deleteAccount(
                eq(target), eq(ACTOR_ID), eq(UserDeletionEvent.Reason.ADMIN_ACTION));
        verify(auditLogService).record(eq(ACTOR_ID), any(), eq(AuditAction.USER_DELETED),
                any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("a non-admin target is unaffected by the admin-count guards")
    void ordinaryUserIsNotProtected() {
        User target = User.builder().id(3L).username("u3").email("u3@example.test")
                .roles(new LinkedHashSet<>(Set.of(Roles.USER))).enabled(true).build();
        given(target);
        when(userRepository.countByRole(Roles.ADMIN)).thenReturn(1L);

        service.deleteUser(actor, 3L, request);

        verify(accountDeletionService).deleteAccount(eq(target), eq(ACTOR_ID), any());
    }

    // --- helpers ----------------------------------------------------------------

    private void given(User target) {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    }

    private static User admin(long id) {
        return User.builder()
                .id(id)
                .username("admin" + id)
                .email("admin" + id + "@example.test")
                .roles(new LinkedHashSet<>(List.of(Roles.ADMIN, Roles.USER)))
                .enabled(true)
                .build();
    }
}
