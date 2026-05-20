package com.mockbank.payment.dto;

import java.util.UUID;

public record PaymentAcceptedResponse(
  UUID paymentId,
  String state,
  String statusUrl
) {}
