package com.mockbank.customer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends KYC status notification emails.
 * <p>
 * When {@code app.mail.enabled=false} (default in dev), all emails are only
 * logged to the console — no SMTP connection is attempted.
 * <p>
 * To enable real delivery set env vars:
 *   MAIL_ENABLED=true  MAIL_HOST=smtp.example.com  MAIL_USERNAME=...  MAIL_PASSWORD=...
 * <p>
 * Uses {@link ObjectProvider} for the sender so the service starts cleanly
 * even when {@code spring.mail.host} is not configured.
 */
@Service
@Slf4j
public class EmailNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:noreply@mockbank.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void sendKycVerified(String toEmail, String firstName, String temporaryPassword) {
        String subject = "[MockBank] Tài khoản của bạn đã được xác minh";
        String body = String.format(
            "Xin chào %s,\n\n" +
            "Hồ sơ KYC của bạn đã được xác minh thành công!\n\n" +
            "Bạn có thể đăng nhập tại: http://localhost:5173/app/login\n" +
            "  Email: %s\n" +
            "  Mật khẩu tạm thời: %s\n\n" +
            "Vui lòng đổi mật khẩu sau khi đăng nhập lần đầu tại mục Hồ sơ > Đổi mật khẩu.\n\n" +
            "Trân trọng,\nMockBank Support",
            firstName, toEmail, temporaryPassword
        );
        send(toEmail, subject, body);
    }

    public void sendBalanceAdjustment(
            String toEmail,
            String firstName,
            java.math.BigDecimal amount,
            String type,
            String reason,
            java.math.BigDecimal balanceAfter) {
        String action = "CREDIT".equalsIgnoreCase(type) ? "nạp tiền" : "rút tiền";
        String subject = "[MockBank] Thông báo " + action + " tài khoản";
        String body = String.format(
                "Xin chào %s,%n%n" +
                "Tài khoản của bạn vừa được %s.%n" +
                "  Số tiền: %s VND%n" +
                "  Lý do: %s%n" +
                "  Số dư sau giao dịch: %s VND%n%n" +
                "Nếu bạn không thực hiện yêu cầu này, vui lòng liên hệ hỗ trợ ngay.%n%n" +
                "Trân trọng,%nMockBank Support",
                firstName,
                action,
                amount.toPlainString(),
                reason,
                balanceAfter.toPlainString());
        send(toEmail, subject, body);
    }

    public void sendKycRejected(String toEmail, String firstName) {
        String subject = "[MockBank] Hồ sơ KYC chưa được chấp thuận";
        String body = String.format(
            "Xin chào %s,\n\n" +
            "Rất tiếc, hồ sơ KYC của bạn chưa đáp ứng yêu cầu xác minh.\n\n" +
            "Vui lòng liên hệ bộ phận hỗ trợ để được hướng dẫn cập nhật hồ sơ.\n" +
            "  Email hỗ trợ: support@mockbank.local\n\n" +
            "Trân trọng,\nMockBank Support",
            firstName
        );
        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String body) {
        log.info("Email notification requested. To={}, Subject={}", to, subject);

        if (!mailEnabled) {
            log.info("Mail not enabled (app.mail.enabled=false). No SMTP delivery attempted.");
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Email delivery is enabled but JavaMailSender is not configured.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Email successfully sent to {}", to);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email notification.", e);
        }
    }
}
