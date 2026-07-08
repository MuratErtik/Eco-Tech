package org.murat.samsungichackathon.repository;




import org.murat.samsungichackathon.enums.CampaignStatus;
import org.murat.samsungichackathon.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    List<Campaign> findByStatusOrderByCreatedAtDesc(CampaignStatus status);
    List<Campaign> findAllByOrderByCreatedAtDesc();
}
