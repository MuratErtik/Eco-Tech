package org.murat.samsungichackathon.model;

// model/Campaign.java


import jakarta.persistence.*;
import org.murat.samsungichackathon.enums.CampaignStatus;

import java.time.Instant;

/**
 * Audit trail'in kendisi: her uretim, girdisi + ciktisi + uretim
 * parametreleri + tam prompt metniyle birlikte tek kayitta saklanir.
 * "Her kayit kendi kanitini tasir" (Accountability).
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String userInput;

    // Uretilen 3 format - uzunluk kestirilemez, TEXT kolonu
    @Column(columnDefinition = "TEXT")
    private String instagramPost;

    @Column(columnDefinition = "TEXT")
    private String linkedinEmail;

    @Column(columnDefinition = "TEXT")
    private String smsText;

    // Human-in-the-loop durumu
    @Enumerated(EnumType.STRING)           // ordinal degil string: DB'de okunur kalsin
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(length = 500)
    private String reviewNote;

    private Instant reviewedAt;

    // --- Transparency / Accountability metadata ---
    @Column(nullable = false, length = 50)
    private String modelName;

    private Double temperature;

    @Column(nullable = false, length = 10)
    private String promptTemplateVersion;

    /** Gemini'ye giden promptun TAM metni - denetlenebilirligin kaniti.
     Response'ta donmuyor, sadece DB'de. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String fullPromptText;

    // --- Safety ---
    @Column(nullable = false)
    private boolean validationPassed;

    /** Uyarilar noktali virgulle ayrilmis tek kolon - hackathon icin
     ayri tablo (@ElementCollection) yerine bilincli sadelik. */
    @Column(length = 500)
    private String warnings;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    // --- getter/setter'lar (Lombok kullanmiyorsan IDE ile uret) ---
    public Long getId() { return id; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String v) { this.userInput = v; }
    public String getInstagramPost() { return instagramPost; }
    public void setInstagramPost(String v) { this.instagramPost = v; }
    public String getLinkedinEmail() { return linkedinEmail; }
    public void setLinkedinEmail(String v) { this.linkedinEmail = v; }
    public String getSmsText() { return smsText; }
    public void setSmsText(String v) { this.smsText = v; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus v) { this.status = v; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String v) { this.reviewNote = v; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; }
    public String getModelName() { return modelName; }
    public void setModelName(String v) { this.modelName = v; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double v) { this.temperature = v; }
    public String getPromptTemplateVersion() { return promptTemplateVersion; }
    public void setPromptTemplateVersion(String v) { this.promptTemplateVersion = v; }
    public String getFullPromptText() { return fullPromptText; }
    public void setFullPromptText(String v) { this.fullPromptText = v; }
    public boolean isValidationPassed() { return validationPassed; }
    public void setValidationPassed(boolean v) { this.validationPassed = v; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String v) { this.warnings = v; }
    public Instant getCreatedAt() { return createdAt; }
}
