/**
 * Catalog of auditable security events in the auth service.
 * Used by AuditLog entity to enforce valid action values via DB CHECK constraint.
 * Covers login, registration, token operations, and API key lifecycle.
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
    PASSWORD_CHANGE_FAILED
}
