package com.settlement.repo;

import com.settlement.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepo extends JpaRepository<Outbox, Long> {
    @Query(value = "SELECT * FROM outbox WHERE state = 'PENDING' ORDER BY id ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Outbox> findPendingWithLock();
}
