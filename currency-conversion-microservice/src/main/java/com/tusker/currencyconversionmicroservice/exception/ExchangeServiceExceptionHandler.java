package com.tusker.currencyconversionmicroservice.exception;

import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;


@RestControllerAdvice
public class ExchangeServiceExceptionHandler {

    @ExceptionHandler({RetryableException.class, ResourceAccessException.class, CallNotPermittedException.class})
    public ResponseEntity<ProblemDetail> handleExchangeUnavailable(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Currency exchange service is temporarily unavailable"
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
