package net.pautet.softs.demospring.identity.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NetatmoConnectionRepository extends JpaRepository<NetatmoConnection, UUID> {
    Optional<NetatmoConnection> findByUserIdAndPurpose(UUID userId, NetatmoConnection.Purpose purpose);
    Optional<NetatmoConnection> findFirstByUserIsNullAndPurpose(NetatmoConnection.Purpose purpose);
    void deleteByUserId(UUID userId);
}
