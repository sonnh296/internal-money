package com.mockbank.payment.service;

import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.client.BillerInvoiceM2MClient;
import com.mockbank.payment.client.BillerRegistryClient;
import com.mockbank.payment.client.dto.BillerInvoiceSnapshot;
import com.mockbank.payment.dto.BillPayRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillPayValidator {
  private final BillerRegistryClient registry;
  private final BillerInvoiceM2MClient invoices;
  private final AccountM2MClient accounts;

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

    UUID invoiceId;
    try {
      invoiceId = UUID.fromString(r.invoiceReference().trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Mã hóa đơn không hợp lệ.");
    }

    BillerInvoiceSnapshot invoice = loadInvoice(invoiceId);
    if (!"PENDING".equalsIgnoreCase(invoice.status())) {
      throw new IllegalArgumentException("Hóa đơn không ở trạng thái chờ thanh toán.");
    }
    if (r.amount().value().compareTo(invoice.amount()) != 0) {
      throw new IllegalArgumentException("Số tiền thanh toán phải khớp với hóa đơn.");
    }
    if (!r.amount().currency().equalsIgnoreCase(invoice.currency())) {
      throw new IllegalArgumentException("Loại tiền thanh toán phải khớp với hóa đơn.");
    }
    if (!r.billerReferenceNumber().equals(invoice.billerReferenceNumber())) {
      throw new IllegalArgumentException("Mã dịch vụ thanh toán không khớp hóa đơn.");
    }

    String accountOwner = accounts.getOwner(r.debtorAccountId()).getCustomerId();
    if (!accountOwner.equals(invoice.customerId())) {
      throw new IllegalArgumentException("Hóa đơn không thuộc chủ tài khoản thanh toán.");
    }
  }

  private BillerInvoiceSnapshot loadInvoice(UUID invoiceId) {
    try {
      return invoices.getInvoice(invoiceId);
    } catch (FeignException ex) {
      if (ex.status() == 404) {
        throw new IllegalArgumentException("Không tìm thấy hóa đơn.");
      }
      throw ex;
    }
  }
}
