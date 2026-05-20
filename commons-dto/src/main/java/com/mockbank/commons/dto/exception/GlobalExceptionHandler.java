package com.mockbank.commons.dto.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.validation.FieldError;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;

import com.mockbank.commons.dto.ErrorResponse;

/**
 * Centralized error mapping to your commons-dto envelope.
 * Status codes align with your OpenAPI conventions.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(err("CONFLICT", ex.getMessage(), ex.getMessage()));
    }

    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.debug("AuthenticationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(err("INVALID_JWT", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn", ex.getMessage()));
    }
 
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleJwtAuthenticationException(JwtAuthenticationException ex) {
        log.debug("JwtAuthenticationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(err("INVALID_CREDS", ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.debug("AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(err("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", ex.getMessage()));
    }


    // ---------- Domain Not Found ----------

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(err("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản", ex.getMessage()));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(err("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng", ex.getMessage()));
    }

    @ExceptionHandler(ConsentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConsentNotFound(ConsentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(err("CONSENT_MISSING", "Consent Missing", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(err("NOT_FOUND", "Không tìm thấy dữ liệu", ex.getMessage()));
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePreconditionRequired(PreconditionRequiredException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(err("PRECONDITION_REQUIRED", ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(err("STATE_CONFLICT", ex.getMessage(), ex.getMessage()));
    }

    // ---------- Business / State ----------

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        String details = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Không đủ số dư khả dụng.";
        String message = details.contains("đang giữ")
                ? "Số dư khả dụng không đủ (có khoản tiền đang chờ xử lý thanh toán hóa đơn)."
                : "Không đủ số dư khả dụng để thực hiện giao dịch.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("INSUFFICIENT_FUNDS", message, details));
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTransitionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("INVALID_TRANSITION", ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(VersionMismatchException.class)
    public ResponseEntity<ErrorResponse> handleVersionMismatch(VersionMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(err(HttpStatus.CONFLICT, "VERSION_MISMATCH", "Stale version or ETag", ex.getMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(err(HttpStatus.CONFLICT, "VERSION_MISMATCH",
                        "Giao dịch bị xung đột. Vui lòng tải lại và thử lại.", ex.getMessage()));
    }

    

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(UpstreamException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(err("UPSTREAM_ERROR", ex.getMessage(), ex.getMessage()));
    }

    // ---------- Validation ----------

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        String vi = ErrorMessageTranslator.toVietnamese(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("BAD_REQUEST", vi, vi));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("VALIDATION_ERROR", "Dữ liệu không hợp lệ", ErrorMessageTranslator.toVietnamese(ex.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String detail = (ex.getMostSpecificCause() == null)
                ? ex.getMessage()
                : ex.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("VALIDATION_ERROR", "Định dạng yêu cầu không hợp lệ", detail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(MethodArgumentNotValidException ex) {
        // Build a single envelope with first message + all field messages in details
        String firstMessage = "Dữ liệu nhập không hợp lệ";
        String firstField = null;
        StringBuilder all = new StringBuilder();

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        for (FieldError fe : fieldErrors) {
            if (firstField == null) {
                firstField = fe.getField();
                if (fe.getDefaultMessage() != null) {
                    firstMessage = ErrorMessageTranslator.toVietnamese(fe.getDefaultMessage());
                }
            }
            if (all.length() > 0) all.append("; ");
            all.append(fe.getField()).append(": ").append(fe.getDefaultMessage());
        }

        String details = (firstField != null)
                ? "field=" + firstField + " | " + all.toString()
                : all.toString();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("VALIDATION_ERROR", firstMessage, details));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("VALIDATION_ERROR", ex.getMessage(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String vi = ErrorMessageTranslator.toVietnamese(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(err("VALIDATION_ERROR", vi, vi));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = ex.getReason() != null
                ? ErrorMessageTranslator.toVietnamese(ex.getReason())
                : status.getReasonPhrase();
        String code = switch (status) {
            case UNAUTHORIZED -> "INVALID_CREDENTIALS";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
            default -> status.name();
        };
        return ResponseEntity.status(status)
                .body(err(code, reason, reason));
    }

    // ---------- Fallback ----------

    /**
     * Không trả ex.getMessage() ra ngoài — tránh lộ thông tin nội bộ (DB schema, stack trace, ...).
     * Chỉ trả Trace ID để team ops có thể tra cứu log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("cid");
        }
        log.error("Unhandled exception traceId={}", traceId, ex);
        String safeDetail = "Trace ID: " + (traceId != null && !traceId.isBlank() ? traceId : "N/A");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(err("INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống. Vui lòng thử lại.", safeDetail));
    }

    // ---------- helper ----------
    private ErrorResponse err(String code, String message, String details) {
        return err(null, code, message, details);
    }

    private ErrorResponse err(HttpStatus status, String code, String message, String details) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("cid");
        }
        String type = "https://api.mockbank/errors/" + code.toLowerCase().replace('_', '-');
        return ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .type(type)
                        .title(message)
                        .status(status != null ? status.value() : null)
                        .traceId(traceId)
                        .build())
                .build();
    }

}
