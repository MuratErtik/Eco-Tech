package org.murat.samsungichackathon.dto;



import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.murat.samsungichackathon.enums.CampaignStatus;

/**
 * Human-in-the-loop karari. Gecersiz enum degeri (orn. "PUBLISHED")
 * Jackson tarafindan deserialize edilemez ve global handler'da 400'e cevrilir.
 * DRAFT'a geri donus gibi gecis kurallari service katmaninda denetlenir (409).
 */
public record UpdateStatusRequest(
        @NotNull(message = "status zorunlu: APPROVED veya REJECTED")
        CampaignStatus status,

        @Size(max = 500, message = "reviewNote en fazla 500 karakter")
        String reviewNote
) {}