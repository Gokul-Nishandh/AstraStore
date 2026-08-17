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
