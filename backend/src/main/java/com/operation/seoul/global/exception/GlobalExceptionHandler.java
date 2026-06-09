package com.operation.seoul.global.exception;

import com.operation.seoul.global.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            log.error("api_error code={} method={} path={}",
                    exception.getCode(), request.getMethod(), request.getRequestURI(), exception);
        } else {
            log.warn("api_rejected code={} status={} method={} path={}",
                    exception.getCode(), exception.getStatus().value(), request.getMethod(), request.getRequestURI());
        }
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = switch (status.value()) {
            case 401 -> "AUTH_REQUIRED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> "REQUEST_FAILED";
        };
        String message = exception.getReason() == null ? "요청을 처리할 수 없습니다." : exception.getReason();
        log.warn("request_rejected code={} status={} method={} path={}",
                code, status.value(), request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "입력값을 확인해 주세요." : error.getDefaultMessage())
                .orElse("입력값을 확인해 주세요.");
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_INPUT", message));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        log.warn("invalid_request type={}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_INPUT", "요청 형식과 입력값을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("unhandled_error method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "요청 처리 중 오류가 발생했습니다."));
    }
}
