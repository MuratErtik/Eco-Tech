package org.murat.samsungichackathon.services;

// service/CampaignService.java

import org.murat.samsungichackathon.dto.CampaignResponse;
import org.murat.samsungichackathon.dto.CampaignSummaryResponse;
import org.murat.samsungichackathon.dto.CreateCampaignRequest;
import org.murat.samsungichackathon.dto.UpdateStatusRequest;
import org.murat.samsungichackathon.enums.CampaignStatus;

import java.util.List;

public interface CampaignService {
    /** Gemini cagrisi + dogrulama + DB kaydi. Basarisiz uretim de kaydedilir (audit). */
    CampaignResponse generate(CreateCampaignRequest request);
    List<CampaignSummaryResponse> findAll(CampaignStatus statusFilter); // null = hepsi
    CampaignResponse findById(Long id);
    CampaignResponse updateStatus(Long id, UpdateStatusRequest request);
    void delete(Long id);
}
