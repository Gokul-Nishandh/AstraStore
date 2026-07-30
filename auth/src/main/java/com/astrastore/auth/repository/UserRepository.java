package com.astrastore.auth.repository;

import com.astrastore.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link User} entities.
 * Spring Data JPA generates the implementation automatically.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Looks up a user by email address (used during authentication).
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether a user with the given email already exists.
     */
    boolean existsByEmail(String email);
}
