package net.pautet.softs.demospring.identity.internal.persistence;

import net.pautet.softs.demospring.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);
}
