package net.pautet.softs.demospring.repository;

import net.pautet.softs.demospring.entity.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

public interface UserRepository extends JpaRepository<User, String> {
    @Cacheable("users")
    User findByUsername(String username);

    @Override
    @CacheEvict(value = "users", key = "#p0.username")
    @NonNull <S extends User> S save(@NonNull S service);
}