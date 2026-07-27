package com.hhclicker.service;

import com.hhclicker.dto.response.CampaignResponse;
import com.hhclicker.entity.ApplicationCampaign;
import com.hhclicker.entity.CampaignVacancy;
import com.hhclicker.enumeration.ApplicationStatus;
import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import com.hhclicker.worker.CampaignExecutionWorker;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {
    private final ApplicationCampaignRepository campaigns;
    private final CampaignVacancyRepository vacancies;
    private final CampaignService campaignService;
    private final TaskExecutor campaignTaskExecutor;
    private final CampaignExecutionWorker worker;

    public ApplicationService(
        ApplicationCampaignRepository campaigns,
        CampaignVacancyRepository vacancies,
        CampaignService campaignService,
        TaskExecutor campaignTaskExecutor,
        CampaignExecutionWorker worker
    ) {
        this.campaigns = campaigns;
        this.vacancies = vacancies;
        this.campaignService = campaignService;
        this.campaignTaskExecutor = campaignTaskExecutor;
        this.worker = worker;
    }

    @Transactional
    public CampaignResponse start(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = campaignService.requireOwned(userId, campaignId);
        if (campaign.getStatus() == CampaignStatus.APPLYING || campaign.getStatus() == CampaignStatus.STOPPING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", "Кампания уже выполняется");
        }
        if (!(campaign.getStatus() == CampaignStatus.READY_TO_APPLY
            || campaign.getStatus() == CampaignStatus.PREVIEW_READY
            || campaign.getStatus() == CampaignStatus.COMPLETED
            || campaign.getStatus() == CampaignStatus.STOPPED
            || campaign.getStatus() == CampaignStatus.INTERRUPTED)) {
            throw new BusinessException("CAMPAIGN_NOT_READY", "Кампания не готова к отправке");
        }
        validateReadyToApply(campaign);
        campaign.setStatus(CampaignStatus.APPLYING);
        campaign.setStopRequested(false);
        campaign.setStartedAt(Instant.now());
        campaign.setFinishedAt(null);
        ApplicationCampaign saved = campaigns.save(campaign);
        campaignTaskExecutor.execute(() -> worker.run(saved.getId()));
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse stop(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = campaignService.requireOwned(userId, campaignId);
        campaign.setStopRequested(true);
        if (campaign.getStatus() == CampaignStatus.APPLYING || campaign.getStatus() == CampaignStatus.LETTERS_GENERATING) {
            campaign.setStatus(CampaignStatus.STOPPING);
        }
        return CampaignResponse.from(campaigns.save(campaign));
    }

    private void validateReadyToApply(ApplicationCampaign campaign) {
        List<CampaignVacancy> selected = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaign.getId()).stream()
            .filter(CampaignVacancy::isSelected)
            .filter(v -> !v.isAlreadyApplied())
            .filter(v -> v.getApplyStatus() != ApplicationStatus.SENT && v.getApplyStatus() != ApplicationStatus.ALREADY_APPLIED)
            .toList();
        if (selected.isEmpty()) {
            throw new BusinessException("CAMPAIGN_NOT_READY", "Нет выбранных вакансий для отправки");
        }
        String mode = normalizeMode(campaign.getCoverLetterMode());
        if ("COMMON".equals(mode)) {
            if (campaign.getCommonCoverLetter() == null || campaign.getCommonCoverLetter().isBlank()) {
                throw new BusinessException("COVER_LETTERS_NOT_READY", "Для общего режима нужно заполнить общее сопроводительное письмо");
            }
            return;
        }
        if ("NONE".equals(mode) || !campaign.isReviewCoverLettersBeforeApply()) {
            return;
        }
        long notReady = selected.stream()
            .filter(v -> v.getCoverLetterStatus() != CoverLetterStatus.GENERATED && v.getCoverLetterStatus() != CoverLetterStatus.EDITED)
            .count();
        if (notReady > 0) {
            throw new BusinessException(
                "COVER_LETTERS_NOT_READY",
                "Не все выбранные вакансии имеют готовые письма: осталось " + notReady
            );
        }
    }

    public String resolveCoverLetter(ApplicationCampaign campaign, CampaignVacancy vacancy) {
        String mode = normalizeMode(campaign.getCoverLetterMode());
        if ("COMMON".equals(mode)) {
            return campaign.getCommonCoverLetter() == null ? "" : campaign.getCommonCoverLetter();
        }
        if ("NONE".equals(mode)) {
            return "";
        }
        return vacancy.getCoverLetter() == null ? "" : vacancy.getCoverLetter();
    }

    private String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PERSONAL_AI";
        }
        return "personal".equalsIgnoreCase(raw) ? "PERSONAL_AI" : raw.strip().toUpperCase();
    }
}
