package org.murat.samsungichackathon.services.impl;

// service/CampaignServiceImpl.java

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.murat.samsungichackathon.configs.AiConfig;
import org.murat.samsungichackathon.dto.CampaignResponse;
import org.murat.samsungichackathon.dto.CampaignSummaryResponse;
import org.murat.samsungichackathon.dto.CreateCampaignRequest;
import org.murat.samsungichackathon.dto.UpdateStatusRequest;
import org.murat.samsungichackathon.enums.CampaignStatus;
import org.murat.samsungichackathon.exceptions.AiProviderException;
import org.murat.samsungichackathon.exceptions.AiResponseValidationException;
import org.murat.samsungichackathon.exceptions.CampaignNotFoundException;
import org.murat.samsungichackathon.exceptions.InvalidStatusTransitionException;
import org.murat.samsungichackathon.model.Campaign;
import org.murat.samsungichackathon.repository.CampaignRepository;
import org.murat.samsungichackathon.services.CampaignService;
import org.murat.samsungichackathon.services.PromptTemplateV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service

public class CampaignServiceImpl implements CampaignService {


    private  final AiConfig aiConfig;

    private final  AiConfig geminiRetryTemplate;

    private static final Logger log = LoggerFactory.getLogger(CampaignServiceImpl.class);

    /** Sayisal iddia kaliplari: %30, 30%, "500 ton", "2 milyon", "1.5 kg" vb. */
    private static final Pattern STAT_PATTERN = Pattern.compile(
            "(%\\s?\\d+|\\d+\\s?%|\\d[\\d.,]*\\s?(ton|kg|adet|milyon|milyar|bin)\\b)",
            Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final CampaignRepository repository;
    private final ObjectMapper objectMapper;

    // Config'den okunuyor -> model degisirse audit trail dogru kalir
    @Value("${spring.ai.google.genai.chat.options.model}")
    private String modelName;

    @Value("${spring.ai.google.genai.chat.options.temperature}")
    private Double temperature;

    // Dogrulanmis kampanya verileri; su an bos, hackathon'da gercek veri
    // eklersen application.yml'e "app.verified-facts" olarak koy
    @Value("${app.verified-facts:}")
    private String verifiedFacts;

    public CampaignServiceImpl(AiConfig aiConfig, AiConfig geminiRetryTemplate, ChatClient chatClient,
                               CampaignRepository repository,
                               ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.geminiRetryTemplate = geminiRetryTemplate;
        this.chatClient = chatClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // DIKKAT: bilincli olarak @Transactional YOK - aciklama asagida
    @Override
    public CampaignResponse generate(CreateCampaignRequest request) {
        String prompt = PromptTemplateV1.build(verifiedFacts, request.idea());

        // 1) Gemini cagrisi - ag/timeout/5xx hatalari 502'ye cevrilir
        String rawResponse;
        try {
            rawResponse = geminiRetryTemplate.geminiRetryTemplate().execute(ctx -> {
                if (ctx.getRetryCount() > 0) {
                    log.warn("Gemini retry denemesi #{}", ctx.getRetryCount());
                }
                return chatClient.prompt().user(prompt).call().content();
            });
        } catch (Exception e) {
            log.error("Gemini cagrisi retry'lar tukendikten sonra basarisiz. idea='{}'",
                    request.idea(), e);
            // Accountability: basarisiz DENEME de kalici kayda gecer
            Campaign failed = baseCampaign(request.idea(), prompt);
            failed.setStatus(CampaignStatus.REJECTED);
            failed.setValidationPassed(false);
            failed.setReviewNote("OTOMATIK RED: AI saglayicisina ulasilamadi - " + e.getMessage());
            failed.setReviewedAt(Instant.now());
            repository.save(failed);
            throw new AiProviderException(
                    "AI servisine su an ulasilamiyor, lutfen birazdan tekrar deneyin.", e);
        }

        // 2) Parse + yapisal dogrulama. Basarisizsa REJECTED olarak KAYDET,
        //    sonra 422 firlat -> basarisiz uretimler de audit trail'de.
        CampaignResponse.GeneratedContent content;
        try {
            content = parseAndValidate(rawResponse);
        } catch (AiResponseValidationException e) {
            Campaign failed = baseCampaign(request.idea(), prompt);
            failed.setStatus(CampaignStatus.REJECTED);
            failed.setValidationPassed(false);
            failed.setReviewNote("OTOMATIK RED: " + e.getMessage());
            failed.setReviewedAt(Instant.now());
            repository.save(failed);
            throw e;
        }

        // 3) Soft safety taramasi: girdi disi sayisal iddia var mi?
        List<String> warnings = scanForWarnings(request.idea(), content);

        // 4) Basarili kayit
        Campaign campaign = baseCampaign(request.idea(), prompt);
        campaign.setInstagramPost(content.instagramPost());
        campaign.setLinkedinEmail(content.linkedinEmail());
        campaign.setSmsText(content.smsText());
        campaign.setValidationPassed(true);
        campaign.setWarnings(warnings.isEmpty() ? null : String.join(";", warnings));
        return toResponse(repository.save(campaign));
    }

    /** Ortak metadata alanlarini dolduran fabrika metodu. */
    private Campaign baseCampaign(String idea, String fullPrompt) {
        Campaign c = new Campaign();
        c.setUserInput(idea);
        c.setModelName(modelName);
        c.setTemperature(temperature);
        c.setPromptTemplateVersion(PromptTemplateV1.VERSION);
        c.setFullPromptText(fullPrompt);
        return c;
    }

    /** Code fence temizligi + JSON parse + alan dogrulamasi. */
    private CampaignResponse.GeneratedContent parseAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiResponseValidationException("Model bos yanit dondurdu.");
        }
        // Prompt'a ragmen model bazen ```json ... ``` sarar - savunmaci temizlik
        String cleaned = raw.strip()
                .replaceAll("^```(json)?\\s*", "")
                .replaceAll("\\s*```$", "");

        CampaignResponse.GeneratedContent content;
        try {
            content = objectMapper.readValue(cleaned, CampaignResponse.GeneratedContent.class);
        } catch (Exception e) {
            throw new AiResponseValidationException(
                    "Model ciktisi gecerli JSON degil: " + e.getMessage());
        }
        if (isBlank(content.instagramPost())) throw missing("instagramPost");
        if (isBlank(content.linkedinEmail()))  throw missing("linkedinEmail");
        if (isBlank(content.smsText()))        throw missing("smsText");
        return content;
    }

    /** Ciktida gecen ama kullanici girdisinde OLMAYAN sayisal iddialari isaretle. */
    private List<String> scanForWarnings(String userInput,
                                         CampaignResponse.GeneratedContent content) {
        List<String> warnings = new ArrayList<>();
        String allOutput = content.instagramPost() + " "
                + content.linkedinEmail() + " " + content.smsText();

        Matcher m = STAT_PATTERN.matcher(allOutput);
        while (m.find()) {
            // Ayni kalip girdide de varsa kullanicinin verisidir, sorun yok
            if (!userInput.contains(m.group().strip())) {
                warnings.add("CONTAINS_UNVERIFIED_STATISTIC");
                break; // tek uyari yeter
            }
        }
        // Model kural geregi etiket kullandiysa insana gorunur kilalim
        if (allOutput.contains("[KAYNAK DOGRULANMALI]")) {
            warnings.add("CONTAINS_VERIFICATION_PLACEHOLDER");
        }
        if (content.smsText().length() > 160) {
            warnings.add("SMS_LENGTH_EXCEEDED");
        }
        return warnings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> findAll(CampaignStatus statusFilter) {
        List<Campaign> list = (statusFilter == null)
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(statusFilter);
        return list.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResponse findById(Long id) {
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new CampaignNotFoundException(id.toString())));
    }

    @Override
    @Transactional
    public CampaignResponse updateStatus(Long id, UpdateStatusRequest request) {
        Campaign campaign = repository.findById(id)
                .orElseThrow(() -> new CampaignNotFoundException(id.toString()));

        // Durum makinesi: yalnizca DRAFT'tan gecis (human-in-the-loop tek seviye)
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidStatusTransitionException(
                    "Yalnizca DRAFT durumundaki kampanyalarin durumu degistirilebilir "
                            + "(mevcut: " + campaign.getStatus() + ")");
        }
        if (request.status() == CampaignStatus.DRAFT) {
            throw new InvalidStatusTransitionException(
                    "Hedef durum APPROVED veya REJECTED olmali.");
        }
        campaign.setStatus(request.status());
        campaign.setReviewNote(request.reviewNote());
        campaign.setReviewedAt(Instant.now());
        return toResponse(campaign); // @Transactional dirty checking ile flush eder
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new CampaignNotFoundException(id.toString());
        repository.deleteById(id);
    }

    // --- mapping ---
    private CampaignResponse toResponse(Campaign c) {
        return new CampaignResponse(
                c.getId(), c.getUserInput(),
                new CampaignResponse.GeneratedContent(
                        c.getInstagramPost(), c.getLinkedinEmail(), c.getSmsText()),
                c.getStatus(), c.getReviewNote(), c.getReviewedAt(),
                new CampaignResponse.AiMetadata("AI", "Google Gemini",
                        c.getModelName(), c.getTemperature(), c.getPromptTemplateVersion()),
                new CampaignResponse.SafetyInfo(c.isValidationPassed(),
                        c.getWarnings() == null ? List.of()
                                : List.of(c.getWarnings().split(";"))),
                c.getCreatedAt());
    }

    private CampaignSummaryResponse toSummary(Campaign c) {
        return new CampaignSummaryResponse(c.getId(), c.getUserInput(),
                c.getStatus(), c.getModelName(), c.getCreatedAt());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static AiResponseValidationException missing(String field) {
        return new AiResponseValidationException(
                "Model ciktisi beklenen JSON semasina uymuyor: " + field + " alani eksik/bos");
    }
}