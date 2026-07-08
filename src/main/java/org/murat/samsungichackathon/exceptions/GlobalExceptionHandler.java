package org.murat.samsungichackathon.exceptions;

// exception/GlobalExceptionHandler.java



import org.murat.samsungichackathon.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Tum hata turleri icin tutarli format: {timestamp, status, error, message}.
 * Hata durumlarinda da seffaflik (Transparency) - istemci neyin, neden
 * basarisiz oldugunu her zaman ayni yapida gorur.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 400 - DTO validasyon hatalari (@NotBlank, @Size vb.) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Tum alan hatalarini tek okunur mesajda birlestir
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
    }

    /** 400 - Bozuk JSON body veya gecersiz enum degeri (orn. status: "PUBLISHED") */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Istek govdesi okunamadi. status alani yalnizca APPROVED veya REJECTED olabilir.");
    }

    /** 400 - Query param'da gecersiz enum (orn. ?status=FOO) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Gecersiz parametre degeri: " + ex.getName());
    }

    /** 503 - AI saglayicisi gecici olarak hizmet veremiyor (retry'lar tukendi) */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")   // saniye cinsinden tekrar deneme onerisi
                .body(new ErrorResponse(Instant.now(), 503, "AI_SERVICE_UNAVAILABLE",
                        "AI servisine su an ulasilamiyor, lutfen birazdan tekrar deneyin."));
    }

    /** 404 - Bilinmeyen URL (Spring Boot 3.x) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Istenen kaynak bulunamadi: " + ex.getResourcePath());
    }

    /** 404 */
    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CampaignNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", ex.getMessage());
    }

    /** 409 - Human-in-the-loop durum makinesi ihlali */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleTransition(InvalidStatusTransitionException ex) {
        return build(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", ex.getMessage());
    }

    /** 422 - Model yanit verdi ama cikti dogrulamadan gecemedi (Safety katmani) */
    @ExceptionHandler(AiResponseValidationException.class)
    public ResponseEntity<ErrorResponse> handleAiValidation(AiResponseValidationException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "AI_RESPONSE_INVALID", ex.getMessage());
    }



    /** 500 - Beklenmeyen her sey (DB hatasi dahil). Ic detay sizdirmiyoruz. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Beklenmeyen bir hata olustu.");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), code, message));
    }
}
