package org.murat.samsungichackathon.dto;

// dto/ErrorResponse.java


import java.time.Instant;

/** Tutarli hata formati - hata durumlarinda da seffaflik (Transparency). */
public record ErrorResponse(Instant timestamp, int status, String error, String message) {}
