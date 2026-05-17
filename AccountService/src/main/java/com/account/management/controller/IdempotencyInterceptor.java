package com.account.management.controller;

import com.account.management.model.IdempotencyRecord;
import com.account.management.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyRecordRepository idempotencyRepo;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(400); // Bad Request
            response.getWriter().write("{\"error\": \"Idempotency-Key header is required for POST requests\"}");
            return false;
        }

        Optional<IdempotencyRecord> recordOpt = idempotencyRepo.findById(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if ("PROCESSING".equals(record.getStatus())) {
                response.setStatus(409); // Conflict
                response.getWriter().write("{\"error\": \"Concurrent request processing\"}");
                return false;
            }
            log.info("Idempotency hit! Returning cached response for key: {}", idempotencyKey);
            response.setStatus(record.getResponseStatus());
            response.setContentType("application/json");
            response.getWriter().write(record.getResponseSnapshot());
            return false;
        }

        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .serviceName("account-service")
                    .status("PROCESSING")
                    .build();
            idempotencyRepo.save(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            response.setStatus(409);
            response.getWriter().write("{\"error\": \"Concurrent request processing\"}");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || response.getStatus() >= 400) {
            // Không lưu cache nếu request lỗi hoặc không có key
            return;
        }

        if (response instanceof ContentCachingResponseWrapper wrapper) {
            byte[] responseArray = wrapper.getContentAsByteArray();
            String responseStr = new String(responseArray, wrapper.getCharacterEncoding());

            try {
                idempotencyRepo.findById(idempotencyKey).ifPresent(record -> {
                    record.setStatus("COMPLETED");
                    record.setResponseStatus(response.getStatus());
                    record.setResponseSnapshot(responseStr);
                    idempotencyRepo.save(record);
                });
            } catch (Exception e) {
                log.error("Failed to save idempotency record: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to save idempotency record", e);
            }
        }
    }
}
