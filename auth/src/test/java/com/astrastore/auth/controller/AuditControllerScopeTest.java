/**
 * The audit endpoint's access rule.
 *
 * <p>{@code userId} is a query parameter on a URL anyone authenticated can
 * hit, so the only thing standing between a curious user and somebody else's
 * login history is that the controller ignores what they sent. These tests
 * assert on the id actually handed to the query service, because that — not
 * the parameter — is what reaches the database.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.AuditLogResponse;
import com.astrastore.auth.dto.PageResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.security.CallerContext;
import com.astrastore.auth.security.Roles;
import com.astrastore.auth.service.AuditLogService;
import com.astrastore.auth.service.AuditQuery;
import com.astrastore.auth.service.AuditQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditControllerScopeTest {

    private static final long CALLER_ID = 7L;
    private static final long SOMEBODY_ELSE_ID = 99L;

    private AuditQueryService auditQueryService;
    private AuditLogService auditLogService;
    private CallerContext callerContext;
    private AuditController controller;

    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        auditQueryService = mock(AuditQueryService.class);
        auditLogService = mock(AuditLogService.class);
        callerContext = mock(CallerContext.class);
        controller = new AuditController(auditQueryService, auditLogService, callerContext);
        httpRequest = new MockHttpServletRequest();

        when(auditQueryService.toPageable(any(), any(), any()))
                .thenReturn(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp")));
        when(auditQueryService.toSort(any())).thenReturn(Sort.by(Sort.Direction.DESC, "timestamp"));
        when(auditQueryService.search(any(), any(), any())).thenReturn(emptyPage());
        when(auditQueryService.distinctActions(any())).thenReturn(List.of(AuditAction.LOGIN_SUCCESS));
    }

    // --- the rule -----------------------------------------------------------

    @Test
    @DisplayName("a non-admin asking for another user's rows is silently scoped to their own")
    void nonAdminCannotReadAnotherUsersRows() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        controller.search(SOMEBODY_ELSE_ID, null, null, null, null, null, null, null, null, httpRequest);

        assertThat(capturedScope()).isEqualTo(CALLER_ID);
    }

    @Test
    @DisplayName("a non-admin who omits userId is scoped to their own rows, not cluster-wide")
    void nonAdminWithoutUserIdIsStillScoped() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        controller.search(null, null, null, null, null, null, null, null, null, httpRequest);

        assertThat(capturedScope()).isEqualTo(CALLER_ID);
    }

    @Test
    @DisplayName("a DEVELOPER is no more privileged here than a USER")
    void nonAdminRoleOtherThanUserIsAlsoScoped() {
        givenCaller(user(CALLER_ID, Roles.DEVELOPER), false);

        controller.search(SOMEBODY_ELSE_ID, null, null, null, null, null, null, null, null, httpRequest);

        assertThat(capturedScope()).isEqualTo(CALLER_ID);
    }

    @Test
    @DisplayName("the CSV export applies the same scoping as the listing")
    void nonAdminExportIsScopedToOwnRows() throws Exception {
        givenCaller(user(CALLER_ID, Roles.USER), false);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        controller.exportCsv(SOMEBODY_ELSE_ID, null, null, null, null, null, null,
                httpRequest, httpResponse);

        ArgumentCaptor<Long> scope = ArgumentCaptor.forClass(Long.class);
        verify(auditQueryService).writeCsv(any(AuditQuery.class), scope.capture(), any(Sort.class), any());
        assertThat(scope.getValue()).isEqualTo(CALLER_ID);

        assertThat(httpResponse.getContentType()).startsWith("text/csv");
        assertThat(httpResponse.getHeader("Content-Disposition")).startsWith("attachment;");
    }

    @Test
    @DisplayName("the actions dropdown is scoped too, so it cannot leak another user's activity")
    void nonAdminActionListIsScoped() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        controller.actions(SOMEBODY_ELSE_ID);

        verify(auditQueryService).distinctActions(eq(CALLER_ID));
    }

    @Test
    @DisplayName("a non-admin's read is not written to the trail")
    void nonAdminReadIsNotAudited() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        controller.search(SOMEBODY_ELSE_ID, null, null, null, null, null, null, null, null, httpRequest);

        verify(auditLogService, org.mockito.Mockito.never())
                .record(any(), any(), eq(AuditAction.AUDIT_LOG_VIEWED), any(), anyBoolean(), any(), any());
    }

    // --- the administrator's side of the same rule ---------------------------

    @Test
    @DisplayName("an admin may narrow the view to one user")
    void adminMayReadAnotherUser() {
        givenCaller(user(1L, Roles.ADMIN), true);

        controller.search(SOMEBODY_ELSE_ID, null, null, null, null, null, null, null, null, httpRequest);

        assertThat(capturedScope()).isEqualTo(SOMEBODY_ELSE_ID);
    }

    @Test
    @DisplayName("an admin who omits userId reads cluster-wide")
    void adminWithoutUserIdReadsEverything() {
        givenCaller(user(1L, Roles.ADMIN), true);

        controller.search(null, null, null, null, null, null, null, null, null, httpRequest);

        assertThat(capturedScope()).isNull();
    }

    @Test
    @DisplayName("an admin reading past their own rows is recorded")
    void adminCrossUserReadIsAudited() {
        givenCaller(user(1L, Roles.ADMIN), true);

        controller.search(SOMEBODY_ELSE_ID, null, null, null, null, null, null, null, null, httpRequest);

        verify(auditLogService).record(eq(1L), any(), eq(AuditAction.AUDIT_LOG_VIEWED),
                any(), eq(true), isNull(), any());
    }

    @Test
    @DisplayName("an admin reading only their own rows is not recorded")
    void adminSelfReadIsNotAudited() {
        givenCaller(user(1L, Roles.ADMIN), true);

        controller.search(1L, null, null, null, null, null, null, null, null, httpRequest);

        verify(auditLogService, org.mockito.Mockito.never())
                .record(any(), any(), eq(AuditAction.AUDIT_LOG_VIEWED), any(), anyBoolean(), any(), any());
    }

    // --- the rule at its source ---------------------------------------------

    @Test
    @DisplayName("AuditQuery.effectiveUserId ignores the requested id for a non-admin")
    void effectiveUserIdIgnoresRequestForNonAdmin() {
        assertThat(AuditQuery.effectiveUserId(SOMEBODY_ELSE_ID, CALLER_ID, false)).isEqualTo(CALLER_ID);
        assertThat(AuditQuery.effectiveUserId(null, CALLER_ID, false)).isEqualTo(CALLER_ID);
        assertThat(AuditQuery.effectiveUserId(SOMEBODY_ELSE_ID, CALLER_ID, true)).isEqualTo(SOMEBODY_ELSE_ID);
        assertThat(AuditQuery.effectiveUserId(null, CALLER_ID, true)).isNull();
    }

    // --- parameter handling --------------------------------------------------

    @Test
    @DisplayName("outcome=all means unfiltered, and an unknown value is a 400 rather than a 500")
    void outcomeIsParsedNotPassedThrough() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        controller.search(null, null, "all", null, null, null, null, null, null, httpRequest);
        assertThat(capturedQuery().success()).isNull();

        assertThatThrownBy(() -> controller.search(
                null, null, "maybe", null, null, null, null, null, null, httpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("success, failure, all");
    }

    @Test
    @DisplayName("a malformed instant is rejected with a message that names the parameter")
    void malformedInstantIsRejected() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        assertThatThrownBy(() -> controller.search(
                null, null, null, "last tuesday", null, null, null, null, null, httpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    @DisplayName("an unknown action names the discovery endpoint instead of the enum class")
    void unknownActionIsRejected() {
        givenCaller(user(CALLER_ID, Roles.USER), false);

        assertThatThrownBy(() -> controller.search(
                null, "NOT_AN_ACTION", null, null, null, null, null, null, null, httpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/auth/audit/actions");
    }

    // --- helpers -------------------------------------------------------------

    private void givenCaller(User caller, boolean admin) {
        when(callerContext.requireUser()).thenReturn(caller);
        when(callerContext.isAdmin(caller)).thenReturn(admin);
    }

    private Long capturedScope() {
        ArgumentCaptor<Long> scope = ArgumentCaptor.forClass(Long.class);
        verify(auditQueryService).search(any(AuditQuery.class), scope.capture(), any());
        return scope.getValue();
    }

    private AuditQuery capturedQuery() {
        ArgumentCaptor<AuditQuery> query = ArgumentCaptor.forClass(AuditQuery.class);
        verify(auditQueryService).search(query.capture(), any(), any());
        return query.getValue();
    }

    private static User user(long id, String role) {
        return User.builder().id(id).username("u" + id).email("u" + id + "@example.test")
                .roles(Set.of(role)).enabled(true).build();
    }

    private static PageResponse<AuditLogResponse> emptyPage() {
        Page<AuditLogResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PageResponse.of(page);
    }
}
