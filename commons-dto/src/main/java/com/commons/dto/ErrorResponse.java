package com.commons.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private ErrorDetail error;

    @Data
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
        private String details;
        /** RFC 7807 — URI định danh loại lỗi */
        private String type;
        private String title;
        private Integer status;
        private String traceId;
    }
}
