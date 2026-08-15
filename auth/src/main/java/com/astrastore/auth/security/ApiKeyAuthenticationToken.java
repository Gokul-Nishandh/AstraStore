/**
 * Marks an authentication as having come from an API key rather than an
 * interactive login.
 *
 * <p>The distinction matters because a key is a long-lived bearer secret that
 * lives in scripts and CI config. Operations that change who you are or
 * destroy an account — editing a profile, changing a password, deleting an
 * account, granting a role — are refused to keys even when the role attached
 * to the key would otherwise permit them. A leaked key should be able to move
 * objects, not seize the account.
 *
 * <p>This mirrors {@code AstraPrincipal.viaApiKey()} in the shared module.
 */
package com.astrastore.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends UsernamePasswordAuthenticationToken {

    public ApiKeyAuthenticationToken(Object principal,
                                     Object credentials,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
    }
}
