package com.hhclicker.repository;

import com.hhclicker.entity.CampaignVacancy;
import com.hhclicker.enumeration.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignVacancyRepository extends JpaRepository<CampaignVacancy, UUID> {
    List<CampaignVacancy> findAllByCampaignIdOrderBySourcePageAscTitleAsc(UUID campaignId);
    Optional<CampaignVacancy> findByIdAndCampaignId(UUID id, UUID campaignId);
    Optional<CampaignVacancy> findByCampaignIdAndHhVacancyId(UUID campaignId, String hhVacancyId);
    long countByCampaignId(UUID campaignId);

    @Query("select v.id from CampaignVacancy v where v.campaign.id = :campaignId")
    List<UUID> findIdsByCampaignId(UUID campaignId);

    @Query("select v.id from CampaignVacancy v where v.campaign.id = :campaignId and v.applyStatus not in :statuses")
    List<UUID> findIdsByCampaignIdAndApplyStatusNotIn(UUID campaignId, List<ApplicationStatus> statuses);

    @Query("""
        select count(v) > 0
        from CampaignVacancy v
        where v.hhAccountId = :hhAccountId
          and v.resumeId = :resumeId
          and v.hhVacancyId = :hhVacancyId
          and v.applyStatus in :statuses
        """)
    boolean existsSuccessfulApplication(UUID hhAccountId, UUID resumeId, String hhVacancyId, List<ApplicationStatus> statuses);

    @Modifying
    @Query("delete from CampaignVacancy v where v.campaign.id in (select c.id from ApplicationCampaign c where c.hhAccount.id = :hhAccountId)")
    void deleteAllByHhAccountId(UUID hhAccountId);

    @Modifying
    @Query("delete from CampaignVacancy v where v.id in :ids")
    void deleteAllByIdIn(Collection<UUID> ids);
}
