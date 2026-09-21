package net.pautet.softs.demospring.identity.internal.persistence;

import org.springframework.data.repository.CrudRepository;
import net.pautet.softs.demospring.identity.User;
import java.util.Optional;

// 💡 CrudRepository is sufficient here, as Redis is a Key-Value store.
public interface RedisUserRepository extends CrudRepository<User, String> {
    // Spring Data automatically implements this derived query method.
    // The key is defined by the @RedisHash annotation on the User entity.
    Optional<User> findByUsername(String username);
}