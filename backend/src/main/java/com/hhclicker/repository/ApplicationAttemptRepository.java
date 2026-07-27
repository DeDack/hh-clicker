package com.hhclicker.repository;

import com.hhclicker.entity.ApplicationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ApplicationAttemptRepository extends JpaRepository<ApplicationAttempt, UUID> {
    List<ApplicationAttempt> findAllByCampaignVacancyIdOrderByAttemptNumberAsc(UUID campaignVacancyId);
    long countByCampaignVacancyId(UUID campaignVacancyId);

    @Modifying
    @Query("delete from ApplicationAttempt a where a.campaignVacancy.id in (select v.id from CampaignVacancy v where v.campaign.hhAccount.id = :hhAccountId)")
    void deleteAllByHhAccountId(UUID hhAccountId);

    @Modifying
    @Query("delete from ApplicationAttempt a where a.campaignVacancy.id in :vacancyIds")
    void deleteAllByCampaignVacancyIds(Collection<UUID> vacancyIds);
}
