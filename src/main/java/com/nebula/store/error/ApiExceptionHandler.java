package com.nebula.store.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates application exceptions into RFC 7807 {@code application/problem+json} responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final URI NOT_FOUND_TYPE = URI.create("https://nebula.store/errors/not-found");
    private static final URI BAD_REQUEST_TYPE = URI.create("https://nebula.store/errors/bad-request");
    private static final URI VALIDATION_TYPE = URI.create("https://nebula.store/errors/validation");

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Не найдено");
        problem.setType(NOT_FOUND_TYPE);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Проверьте корректность полей запроса");
        problem.setTitle("Ошибка валидации");
        problem.setType(VALIDATION_TYPE);

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Некорректное значение параметра '" + ex.getName() + "'");
        problem.setTitle("Некорректный запрос");
        problem.setType(BAD_REQUEST_TYPE);
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleBusinessError(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Некорректный запрос");
        problem.setType(BAD_REQUEST_TYPE);
        return problem;
    }
}
