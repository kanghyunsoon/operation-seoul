package com.operation.seoul.global.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message(message)
                .data(data)
                .requestId(MDC.get("requestId"))
                .build();
    }

    public static ApiResponse<Void> ok(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .code("OK")
                .message(message)
                .requestId(MDC.get("requestId"))
                .build();
    }

    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(code)
                .message(message)
                .requestId(MDC.get("requestId"))
                .build();
    }
}
