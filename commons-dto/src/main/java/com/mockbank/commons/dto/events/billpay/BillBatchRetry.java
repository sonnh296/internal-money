package com.mockbank.commons.dto.events.billpay;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BillBatchRetry {
  private String eventId;
  private String batchId;
  private long backoffMs;
  private String reason;
  private int attempt;
  private String correlationId;
  private String occurredAt;
}
