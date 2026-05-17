package com.billpay.worker.repo;

import com.billpay.worker.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findTop200ByStateOrderByIdAsc(String state);

    @Modifying
    @Query("UPDATE Outbox o SET o.state = :newState, o.updatedAt = current_timestamp WHERE o.id = :id AND o.state = :oldState")
    int markStateIfCurrent(@Param("id") Long id, @Param("oldState") String oldState, @Param("newState") String newState);

    @Modifying
    @Query("UPDATE Outbox o SET o.state = :newState, o.updatedAt = current_timestamp WHERE o.id = :id")
    void updateState(@Param("id") Long id, @Param("newState") String newState);
}
