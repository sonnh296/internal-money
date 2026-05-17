package com.commons.exception;

/**
 * Maps technical error codes/messages to user-facing Vietnamese text.
 */
public final class ErrorMessageTranslator {

    private ErrorMessageTranslator() {}

    public static String toVietnamese(String codeOrMessage) {
        if (codeOrMessage == null || codeOrMessage.isBlank()) {
            return "Đã xảy ra lỗi không xác định.";
        }
        String key = codeOrMessage.trim();
        return switch (key) {
            case "BILLER_INACTIVE" -> "Dịch vụ thanh toán không khả dụng hoặc đã ngừng.";
            case "EXECUTION_DATE_PAST" -> "Ngày thực hiện không được ở quá khứ.";
            case "CURRENCY_NOT_ALLOWED" -> "Chỉ hỗ trợ thanh toán bằng VND.";
            case "CUSTOMER_ID_CLAIM_MISSING" -> "Phiên đăng nhập thiếu thông tin khách hàng.";
            case "REDEEM_FAILED" -> "Không thể đổi điểm thưởng. Vui lòng thử lại.";
            case "REDEEM_EXCEEDS_OR_MATCHES_BILL_AMOUNT" -> "Số điểm đổi vượt quá số tiền hóa đơn.";
            case "Source account id is required" -> "Thiếu tài khoản nguồn.";
            case "Destination account not found" -> "Không tìm thấy tài khoản người nhận.";
            case "Source and destination account must be different" -> "Không thể chuyển cho chính tài khoản nguồn.";
            case "Both accounts must be ACTIVE" -> "Tài khoản nguồn hoặc đích không hoạt động.";
            case "Currency mismatch between source and destination account" ->
                    "Tài khoản nguồn và đích khác loại tiền tệ.";
            case "Account not found" -> "Không tìm thấy tài khoản.";
            case "Something went wrong" -> "Đã xảy ra lỗi hệ thống. Vui lòng thử lại.";
            case "Forbidden" -> "Bạn không có quyền thực hiện thao tác này.";
            case "Input validation failed" -> "Dữ liệu nhập không hợp lệ.";
            case "Constraint violation" -> "Dữ liệu không hợp lệ.";
            case "Malformed JSON request" -> "Định dạng yêu cầu không hợp lệ.";
            default -> {
                if (key.startsWith("REDEEM_FAILED_")) {
                    yield "Đổi điểm thưởng thất bại.";
                }
                if (key.contains("exceeds available balance") || key.contains("Insufficient")) {
                    yield "Không đủ số dư khả dụng.";
                }
                if (key.contains("Due date must be after subscription")) {
                    yield "Ngày đến hạn không hợp lệ với gói dịch vụ.";
                }
                if (key.contains("No active subscriptions")) {
                    yield "Chưa có khách hàng đăng ký gói dịch vụ này.";
                }
                yield key;
            }
        };
    }
}
