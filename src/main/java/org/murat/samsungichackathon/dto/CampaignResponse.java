package org.murat.samsungichackathon.dto;




import org.murat.samsungichackathon.enums.CampaignStatus;

import java.time.Instant;
import java.util.List;

/** Tam kampanya gorunumu - Transparency alanlari (aiMetadata, safety) dahil. */
public record CampaignResponse(
        Long id,
        String userInput,
        GeneratedContent content,
        CampaignStatus status,
        String reviewNote,
        Instant reviewedAt,
        AiMetadata aiMetadata,
        SafetyInfo safety,
        Instant createdAt
) {
    /** Gemini'nin urettigi uc format. */
    public record GeneratedContent(String instagramPost, String linkedinEmail, String smsText) {}

    /** Transparency: icerigin AI urunu oldugu ve uretim parametreleri. */
    public record AiMetadata(String generatedBy, String provider, String modelName,
                             Double temperature, String promptTemplateVersion) {}

    /** Safety: yapisal dogrulama sonucu + icerik uyarilari. */
    public record SafetyInfo(boolean validationPassed, List<String> warnings) {}
}
