package com.mockbank.payment.repo;

import com.mockbank.payment.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepo extends JpaRepository<ProcessedEvent, Long> {
  boolean existsByHandlerAndEventId(String handler, String eventId);
}
