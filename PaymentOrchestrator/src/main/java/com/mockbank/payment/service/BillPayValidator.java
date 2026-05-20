package com.mockbank.payment.service;

import com.mockbank.payment.dto.BillPayRequest;
import com.mockbank.payment.client.BillerRegistryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class BillPayValidator {
  private final BillerRegistryClient registry;

  public void validate(BillPayRequest r) {
    if (!registry.isActive(r.billerReferenceNumber())) {
      throw new IllegalArgumentException("Dịch vụ thanh toán không khả dụng hoặc đã ngừng.");
    }
    var exec = LocalDate.parse(r.executionDate());
    if (exec.isBefore(LocalDate.now())) {
      throw new IllegalArgumentException("Ngày thực hiện không được ở quá khứ.");
    }
    if (!"VND".equals(r.amount().currency())) {
      throw new IllegalArgumentException("Chỉ hỗ trợ thanh toán bằng VND.");
    }
  }
}
