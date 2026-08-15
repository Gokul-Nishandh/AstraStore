/**
 * Catalog of auditable security events in the auth service.
 * Persisted as a string in the {@code audit_logs.action} column.
 *
 * <p>The UI never hardcodes this list — it reads
 * {@code GET /api/auth/audit/actions} — so adding a constant here is enough
 * to make it filterable in the console.
 */
package com.astrastore.auth.entity;

/**
 * Enumeration of audit log actions.
 * Used to track security-relevant events in the auth service.
 */
public enum AuditAction {

    REGISTER_SUCCESS,
    REGISTER_FAILED,

    LOGIN_SUCCESS,
    LOGIN_FAILED,

    LOGOUT,

    // --- Federated sign-in (Google, GitHub) --------------------------------
    /** A returning user signed in through an external provider. */
    OAUTH_SIGN_IN,
    /** A brand-new account was created from a provider's verified identity. */
    OAUTH_ACCOUNT_CREATED,
    /**
     * A provider was attached to an account that already existed here. The
     * single most security-relevant event in the OAuth flow — it is the one
     * that grants a new credential over an existing account — so it is
     * recorded with the address and the provider in the detail.
     */
    OAUTH_ACCOUNT_LINKED,
    /** A provider round-trip that did not end in a session. */
    OAUTH_LOGIN_FAILED,

    REFRESH_TOKEN_SUCCESS,
    REFRESH_TOKEN_FAILED,

    API_KEY_CREATED,
    API_KEY_REVOKED,
    API_KEY_LIST_VIEWED,

    API_KEY_USED,
    API_KEY_USED_FAILED,

    PASSWORD_CHANGED,
    PASSWORD_CHANGE_FAILED,

    // --- Account lifecycle (self-service) --------------------------------
    PROFILE_UPDATED,
    EMAIL_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_RESET_FAILED,
    ACCOUNT_DELETED,

    // --- Administration ---------------------------------------------------
    ADMIN_GRANTED,
    ROLE_CHANGED,
    ROLE_CHANGE_DENIED,
    USER_ENABLED,
    USER_DISABLED,
    USER_DELETED,
    ADMIN_USER_LIST_VIEWED,

    // --- Audit trail itself ------------------------------------------------
    AUDIT_LOG_VIEWED,
    AUDIT_LOG_EXPORTED
}
