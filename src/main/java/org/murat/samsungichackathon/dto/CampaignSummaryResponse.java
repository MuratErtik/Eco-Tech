package org.murat.samsungichackathon.dto;


import org.murat.samsungichackathon.enums.CampaignStatus;

import java.time.Instant;

/** Liste gorunumu - payload'i kucuk tutmak icin ozet alanlar. */
public record CampaignSummaryResponse(
        Long id, String userInput, CampaignStatus status,
        String modelName, Instant createdAt
) {}
