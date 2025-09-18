package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest; // Usa ServletWebRequest para obtener el path
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());

        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        body.put("path", path);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // Por defecto un 500

        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("Stock insuficiente")) {
                status = HttpStatus.CONFLICT; // 409 Conflict
                body.put("error", "Conflict");
                body.put("status", status.value());
            } else if (ex.getMessage().contains("no está activo para la venta")) {
                status = HttpStatus.FORBIDDEN; // 403 Forbidden
                body.put("error", "Forbidden");
                body.put("status", status.value());
            } else if (ex.getMessage().contains("no encontrado")) {
                status = HttpStatus.NOT_FOUND; // 404 Not Found
                body.put("error", "Not Found");
                body.put("status", status.value());
            } else {
                body.put("error", "Internal Server Error");
                body.put("status", status.value());
            }
        } else {
            body.put("error", "Internal Server Error");
            body.put("status", status.value());
        }

        return new ResponseEntity<>(body, status);
    }

    // Puedes añadir más @ExceptionHandler para otras excepciones específicas
    // Por ejemplo, para errores de validación de @Valid:
    // @ExceptionHandler(MethodArgumentNotValidException.class)
    // public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
    //     Map<String, Object> body = new LinkedHashMap<>();
    //     body.put("timestamp", LocalDateTime.now());
    //     body.put("status", HttpStatus.BAD_REQUEST.value());
    //     body.put("error", "Validation Error");
    //     List<String> errors = ex.getBindingResult().getFieldErrors().stream()
    //                             .map(error -> error.getField() + ": " + error.getDefaultMessage())
    //                             .collect(Collectors.toList());
    //     body.put("messages", errors);
    //     String path = ((ServletWebRequest) request).getRequest().getRequestURI();
    //     body.put("path", path);
    //     return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    // }
}