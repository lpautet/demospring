package net.pautet.softs.demospring.operations.internal.persistence;

import net.pautet.softs.demospring.operations.LogMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<LogMessage, Long> {
}