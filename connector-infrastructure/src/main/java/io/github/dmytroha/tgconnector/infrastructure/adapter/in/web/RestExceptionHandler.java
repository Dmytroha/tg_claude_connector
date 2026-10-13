package io.github.dmytroha.tgconnector.infrastructure.adapter.in.web;

import io.github.dmytroha.tgconnector.application.exception.SourceAlreadyRegisteredException;
import io.github.dmytroha.tgconnector.application.exception.SourceConcurrentlyModifiedException;
import io.github.dmytroha.tgconnector.application.exception.SourceNotFoundException;
import io.github.dmytroha.tgconnector.domain.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/application errors to RFC 9457 problem details.
 */
@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(SourceNotFoundException.class)
    ProblemDetail notFound(SourceNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({SourceAlreadyRegisteredException.class, SourceConcurrentlyModifiedException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail invalid(DomainException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
