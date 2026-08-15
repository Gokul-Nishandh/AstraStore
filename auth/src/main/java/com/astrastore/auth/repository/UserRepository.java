package com.astrastore.auth.repository;

import com.astrastore.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link User} entities.
 * Spring Data JPA generates the implementation automatically.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Looks up a user by email address (used during authentication).
     */
    Optional<User> findByEmail(String email);

    /**
     * Case-insensitive email lookup. Addresses are compared case-insensitively
     * everywhere so {@code Alice@x.com} cannot be registered alongside
     * {@code alice@x.com} and used to impersonate it in the console.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Checks whether a user with the given email already exists.
     */
    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByUsername(String username);

    List<User> findAllByUsernameOrEmail(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * How many accounts hold the given role. The last-administrator guard is
     * built on this: demoting, disabling or deleting the final ADMIN would
     * leave the deployment with no way to administer itself.
     */
    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r WHERE r = :role")
    long countByRole(@Param("role") String role);

    /** As {@link #countByRole}, ignoring accounts that are already disabled. */
    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r WHERE r = :role AND u.enabled = true")
    long countEnabledByRole(@Param("role") String role);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findAllByRole(@Param("role") String role);
}
