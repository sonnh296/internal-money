package com.mockbank.payment.repo;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByIdempotencyKey(String idemKey);
  List<Payment> findAllByBatchId(UUID batchId);

  List<Payment> findByStateAndUpdatedAtBefore(PaymentState state, OffsetDateTime updatedAt);

  List<Payment> findByStateInAndUpdatedAtBefore(Collection<PaymentState> states, OffsetDateTime updatedAt);

  List<Payment> findByState(PaymentState state);

  @Modifying
  @Query("update Payment p set p.state=?2, p.updatedAt=current_timestamp where p.paymentId=?1")
  int updateState(String paymentId, PaymentState state);
}
