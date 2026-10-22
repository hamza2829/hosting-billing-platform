package com.hamza.hosting.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<?> databaseConflict(Exception e) {
    return result(409, "Conflicting resource");
  }

  public static class Missing extends RuntimeException {
    public Missing(String message) {
      super(message);
    }
  }

  public static class Conflict extends RuntimeException {
    public Conflict(String message) {
      super(message);
    }
  }

  @ExceptionHandler(Missing.class)
  ResponseEntity<?> missing(Missing e) {
    return result(404, e.getMessage());
  }

  @ExceptionHandler(Conflict.class)
  ResponseEntity<?> conflict(Conflict e) {
    return result(409, e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return result(400, "Invalid request");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    LoggerFactory.getLogger(Errors.class).error("Request failed", e);
    return result(500, "Request failed");
  }

  private ResponseEntity<?> result(int status, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("error", message, "correlationId", Trace.current()));
  }
}
