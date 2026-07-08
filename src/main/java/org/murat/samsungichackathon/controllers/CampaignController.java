package org.murat.samsungichackathon.controllers;

// controller/CampaignController.java



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.murat.samsungichackathon.dto.*;
import org.murat.samsungichackathon.enums.CampaignStatus;
import org.murat.samsungichackathon.services.CampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "Campaigns",
        description = "Eco-Tech Kampanya Motoru - AI destekli, insan onayli kampanya uretimi (FATS ilkeleri)")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    // ---------- 1) URETIM: Accountability + Safety ----------
    @Operation(
            summary = "Yeni kampanya uret",
            description = "Tek cumlelik fikri Gemini'ye gonderir, 3 formatli icerik uretir ve "
                    + "prompt/model metadata'siyla birlikte kalici olarak kaydeder (audit trail). "
                    + "Icerik DRAFT statusunde dogar; insan onayi olmadan APPROVED olamaz.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kampanya uretildi ve kaydedildi",
                    content = @Content(schema = @Schema(implementation = CampaignResponse.class),
                            examples = @ExampleObject(value = """
                    {"id":42,"userInput":"Eski telefonunu getir, geri donusturelim",
                     "content":{"instagramPost":"...","linkedinEmail":"...","smsText":"..."},
                     "status":"DRAFT","reviewNote":null,"reviewedAt":null,
                     "aiMetadata":{"generatedBy":"AI","provider":"Google Gemini",
                       "modelName":"gemini-2.5-flash","temperature":0.7,
                       "promptTemplateVersion":"v1"},
                     "safety":{"validationPassed":true,"warnings":[]},
                     "createdAt":"2026-07-08T14:32:00Z"}"""))),
            @ApiResponse(responseCode = "400", description = "Gecersiz girdi (bos ya da 10-500 karakter disi)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {"timestamp":"2026-07-08T14:32:00Z","status":400,
                     "error":"VALIDATION_ERROR",
                     "message":"idea: Kampanya fikri 10-500 karakter arasinda olmali"}"""))),
            @ApiResponse(responseCode = "422", description = "Gemini yaniti parse edilemedi veya sema dogrulamasindan gecemedi; "
                    + "basarisiz uretim de audit icin REJECTED olarak kaydedilir",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {"timestamp":"2026-07-08T14:32:00Z","status":422,
                     "error":"AI_RESPONSE_INVALID",
                     "message":"Model ciktisi beklenen JSON semasina uymuyor: smsText alani eksik"}"""))),
            @ApiResponse(responseCode = "502", description = "Gemini API'ye ulasilamadi (ag hatasi, timeout, provider 5xx)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {"timestamp":"2026-07-08T14:32:00Z","status":502,
                     "error":"AI_PROVIDER_ERROR",
                     "message":"Gemini API'ye ulasilamadi, kampanya kaydedilmedi"}""")))
    })
    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        CampaignResponse created = campaignService.generate(request);
        // 201 + Location header: REST standardi
        return ResponseEntity.created(URI.create("/api/campaigns/" + created.id())).body(created);
    }

    // ---------- 2) LISTE: Transparency + Accountability ----------
    @Operation(summary = "Kampanyalari listele",
            description = "Gecmis tum uretimler denetlenebilir. status filtresi insan denetiminden "
                    + "gecmemis (DRAFT) icerigi ayirt etmeyi saglar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kampanya listesi (ozet gorunum)"),
            @ApiResponse(responseCode = "400", description = "Gecersiz status degeri",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<CampaignSummaryResponse> list(
            @Parameter(description = "Opsiyonel durum filtresi: DRAFT, APPROVED, REJECTED")
            @RequestParam(required = false) CampaignStatus status) {
        return campaignService.findAll(status);
    }

    // ---------- 3) DETAY: Transparency ----------
    @Operation(summary = "Kampanya detayi",
            description = "Ciktinin hangi girdiyle, hangi model ve parametrelerle, hangi prompt "
                    + "surumuyle ve ne zaman uretildigini tam olarak gosterir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kampanya bulundu"),
            @ApiResponse(responseCode = "404", description = "Kampanya yok",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {"timestamp":"2026-07-08T14:32:00Z","status":404,
                     "error":"CAMPAIGN_NOT_FOUND","message":"Kampanya bulunamadi: id=99"}""")))
    })
    @GetMapping("/{id}")
    public CampaignResponse getById(
            @Parameter(description = "Kampanya ID") @PathVariable Long id) {
        return campaignService.findById(id);
    }

    // ---------- 4) DURUM: Human-in-the-loop ----------
    @Operation(summary = "Kampanya durumunu guncelle (insan onayi)",
            description = "Human-in-the-loop mekanizmasi: yalnizca DRAFT durumundaki bir kampanya "
                    + "APPROVED veya REJECTED yapilabilir. Karar ve zamani audit icin kaydedilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durum guncellendi, reviewedAt dolduruldu"),
            @ApiResponse(responseCode = "400", description = "Gecersiz status degeri (enum disi)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Kampanya yok",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Gecersiz gecis: yalnizca DRAFT'tan gecis yapilabilir",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                    {"timestamp":"2026-07-08T14:32:00Z","status":409,
                     "error":"INVALID_STATUS_TRANSITION",
                     "message":"Yalnizca DRAFT durumundaki kampanyalarin durumu degistirilebilir (mevcut: APPROVED)"}""")))
    })
    @PatchMapping("/{id}/status")
    public CampaignResponse updateStatus(
            @Parameter(description = "Kampanya ID") @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return campaignService.updateStatus(id, request);
    }

    // ---------- 5) SILME: veri sahipligi ----------
    @Operation(summary = "Kampanyayi sil",
            description = "Kullanicinin kendi girdisinden uretilen icerigi kalici olarak kaldirma "
                    + "hakki (basit right-to-erasure karsiligi).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Silindi"),
            @ApiResponse(responseCode = "404", description = "Kampanya yok",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "Kampanya ID") @PathVariable Long id) {
        campaignService.delete(id);
    }
}
