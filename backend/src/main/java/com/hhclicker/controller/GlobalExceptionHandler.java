package com.hhclicker.controller;

import com.hhclicker.dto.response.ErrorResponse;
import com.hhclicker.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleDomain(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getCode()) {
            case "FORBIDDEN", "COVER_LETTER_GENERATION_DISABLED" -> HttpStatus.FORBIDDEN;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        String requestId = request.getHeader("X-Request-Id");
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getCode(), ex.getMessage(), Map.of(), requestId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(java.util.stream.Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() == null ? "Некорректное значение" : error.getDefaultMessage(),
                (first, ignored) -> first
            ));
        String message = fields.containsKey("resumeId")
            ? "Выберите резюме"
            : fields.containsKey("hhAccountId")
                ? "Выберите HH-аккаунт"
                : fields.containsKey("searchUrl")
                    ? "Укажите URL поиска HH"
                    : fields.containsKey("pages")
                        ? "Укажите количество страниц"
                        : "Проверьте поля формы";
        String requestId = request.getHeader("X-Request-Id");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message, Map.copyOf(fields), requestId));
    }
}
