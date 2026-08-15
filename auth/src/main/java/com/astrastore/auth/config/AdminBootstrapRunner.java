/**
 * Runs the ADMIN bootstrap once the application context is up and the schema
 * has been created.
 *
 * <p>Failure here is logged, not fatal. A deployment that cannot reach the
 * database at this instant should still finish starting and let the health
 * check report the real problem, rather than crash-looping with a stack trace
 * about role promotion.
 */
package com.astrastore.auth.config;

import com.astrastore.auth.service.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(10)
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            adminBootstrapService.promoteConfiguredAdmins();
        } catch (Exception e) {
            log.error("Admin bootstrap failed; no account was promoted. "
                    + "Fix the cause and restart, or grant ADMIN through the admin API. Error: {}",
                    e.getMessage(), e);
        }
    }
}
