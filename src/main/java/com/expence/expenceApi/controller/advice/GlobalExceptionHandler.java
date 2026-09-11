package com.expence.expenceApi.controller.advice;

import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into the API's error body: {@code {"message": ...}}, plus an
 * {@code "errors"} map for validation failures.
 *
 * <p>Framework client-error exceptions are handled explicitly. They have to be: handlers on a
 * {@code @RestControllerAdvice} are consulted before Spring's
 * DefaultHandlerExceptionResolver, so without these the catch-all below would swallow a
 * malformed UUID or an unparseable body and report it as a 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = new LinkedHashMap<String, String>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("message", "Validation failed");
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid value for '" + ex.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest("Malformed request body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("Missing required parameter '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(ex.getMessage() != null ? ex.getMessage() : "Invalid request");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Rejected request that violated a database constraint", ex);
        return badRequest("One or more values are invalid or conflict with existing data");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return status(HttpStatus.METHOD_NOT_ALLOWED, ex.getMethod() + " is not supported by this endpoint");
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        var message = ex instanceof NotFoundException ? ex.getMessage() : "Resource not found";
        return status(HttpStatus.NOT_FOUND, message);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return status(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, Object>> status(HttpStatus status, String message) {
        var body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
