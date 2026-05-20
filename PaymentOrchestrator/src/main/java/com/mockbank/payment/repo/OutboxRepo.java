package com.mockbank.payment.repo;

import com.mockbank.payment.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OutboxRepo extends JpaRepository<Outbox, Long> {
  List<Outbox> findTop200ByStateOrderByIdAsc(String state);

  /** Cập nhật trạng thái outbox row riêng lẻ sau khi Kafka send hoàn tất */
  @Modifying
  @Transactional
  @Query("UPDATE Outbox o SET o.state = :state WHERE o.id = :id")
  void updateState(@Param("id") Long id, @Param("state") String state);

  @Modifying
  @Transactional
  @Query("UPDATE Outbox o SET o.state = :newState WHERE o.id = :id AND o.state = :expectedState")
  int markStateIfCurrent(@Param("id") Long id, @Param("expectedState") String expectedState,
      @Param("newState") String newState);

  @Modifying
  @Transactional
  @Query("UPDATE Outbox o SET o.state = 'PENDING' WHERE o.state = 'SENDING'")
  int resetStaleSendingToPending();
}
