/**
 * Self-service account endpoints: the caller's own profile, password and
 * deletion.
 *
 * <p>There is no id anywhere in these paths. The account operated on is
 * always the one behind the bearer token, resolved through
 * {@link CallerContext} — a path variable would be a value the caller
 * chooses, and this controller must never act on an account of the caller's
 * choosing.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.ChangePasswordRequest;
import com.astrastore.auth.dto.DeleteAccountRequest;
import com.astrastore.auth.dto.ProfileUpdateResponse;
import com.astrastore.auth.dto.UpdateProfileRequest;
import com.astrastore.auth.dto.UserProfileResponse;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.security.CallerContext;
import com.astrastore.auth.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CallerContext callerContext;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(AccountService.toProfile(callerContext.requireUser()));
    }

    /**
     * Partial update. When the change invalidates the caller's own tokens the
     * response carries a fresh pair, so a client can swap them in without
     * bouncing the user to the login screen.
     */
    @PatchMapping
    public ResponseEntity<ProfileUpdateResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Changing your profile");
        User caller = callerContext.requireUser();
        return ResponseEntity.ok(accountService.updateProfile(caller, request, httpRequest));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Changing your password");
        User caller = callerContext.requireUser();
        accountService.changePassword(caller, request.currentPassword(), request.newPassword(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes the caller's account. The password is re-confirmed in the body
     * because holding a valid token only proves the session was opened at
     * some point, not that the person at the keyboard owns the account.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest) {

        callerContext.requireInteractiveLogin("Deleting your account");
        User caller = callerContext.requireUser();
        accountService.deleteOwnAccount(caller, request.password(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
