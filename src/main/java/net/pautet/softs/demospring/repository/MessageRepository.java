package net.pautet.softs.demospring.repository;

import net.pautet.softs.demospring.entity.LogMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<LogMessage, Long> {
}