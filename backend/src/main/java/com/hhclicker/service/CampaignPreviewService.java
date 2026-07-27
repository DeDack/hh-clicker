package com.hhclicker.service;

import com.hhclicker.entity.ApplicationCampaign;
import com.hhclicker.entity.CampaignVacancy;
import com.hhclicker.enumeration.ApplicationStatus;
import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.VacancySearchAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.VacancySummaryAdapterResponse;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CampaignPreviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignPreviewService.class);

    private final TaskExecutor campaignTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationCampaignRepository campaigns;
    private final CampaignVacancyRepository vacancies;
    private final HhAccountService accounts;
    private final HhAdapterClient adapterClient;

    public CampaignPreviewService(
        TaskExecutor campaignTaskExecutor,
        TransactionTemplate transactionTemplate,
        ApplicationCampaignRepository campaigns,
        CampaignVacancyRepository vacancies,
        HhAccountService accounts,
        HhAdapterClient adapterClient
    ) {
        this.campaignTaskExecutor = campaignTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.campaigns = campaigns;
        this.vacancies = vacancies;
        this.accounts = accounts;
        this.adapterClient = adapterClient;
    }

    public void start(UUID campaignId) {
        campaignTaskExecutor.execute(() -> run(campaignId));
    }

    private void run(UUID campaignId) {
        try {
            PreviewContext context = transactionTemplate.execute(status -> {
                ApplicationCampaign campaign = campaigns.findById(campaignId).orElseThrow();
                HhSessionAdapterPayload session = accounts.decryptSession(campaign.getHhAccount());
                return new PreviewContext(
                    campaign.getId(),
                    campaign.getHhAccount().getId(),
                    campaign.getResume().getId(),
                    campaign.getResume().getHhResumeId(),
                    session,
                    campaign.getSearchUrl(),
                    campaign.getPages(),
                    campaign.getVacancyLoadLimit(),
                    split(campaign.getIncludeKeywords()),
                    split(campaign.getExcludeKeywords())
                );
            });
            VacancySearchAdapterResponse response = adapterClient.searchVacancies(context.session(), context.searchUrl(), context.pages(), context.hhResumeId());
            List<VacancySummaryAdapterResponse> filtered = limit(
                filterAndDedupe(response.vacancies(), context.includeKeywords(), context.excludeKeywords()),
                context.vacancyLoadLimit()
            );
            transactionTemplate.executeWithoutResult(status -> saveResult(context, filtered));
        } catch (Exception ex) {
            LOGGER.warn("campaign_preview failed campaign={} error={}", campaignId, ex.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                campaigns.findById(campaignId).ifPresent(campaign -> {
                    campaign.setStatus(CampaignStatus.FAILED);
                    campaign.setFailedCount(campaign.getFailedCount() + 1);
                    campaigns.save(campaign);
                });
            });
        }
    }

    private void saveResult(PreviewContext context, List<VacancySummaryAdapterResponse> found) {
        ApplicationCampaign campaign = campaigns.findById(context.campaignId()).orElseThrow();
        int alreadyAppliedByHh = 0;
        int alreadyAppliedByDatabase = 0;
        int added = 0;
        for (VacancySummaryAdapterResponse item : found) {
            boolean appliedByHh = item.alreadyApplied();
            boolean appliedByDatabase = vacancies.existsSuccessfulApplication(
                context.hhAccountId(),
                context.resumeId(),
                item.hhVacancyId(),
                List.of(ApplicationStatus.SENT, ApplicationStatus.ALREADY_APPLIED)
            );
            if (appliedByHh) {
                alreadyAppliedByHh++;
            }
            if (appliedByDatabase) {
                alreadyAppliedByDatabase++;
            }
            boolean alreadyApplied = appliedByHh || appliedByDatabase;
            CampaignVacancy vacancy = vacancies.findByCampaignIdAndHhVacancyId(context.campaignId(), item.hhVacancyId()).orElseGet(CampaignVacancy::new);
            boolean wasSent = vacancy.getApplyStatus() == ApplicationStatus.SENT;
            vacancy.setCampaign(campaign);
            vacancy.setHhVacancyId(item.hhVacancyId());
            vacancy.setHhAccountId(context.hhAccountId());
            vacancy.setResumeId(context.resumeId());
            vacancy.setTitle(item.title());
            vacancy.setVacancyUrl(item.url());
            vacancy.setSourcePage(item.sourcePage());
            if (wasSent) {
                vacancy.setAlreadyApplied(true);
                vacancy.setSelected(false);
                vacancy.setStatus("SENT");
                vacancy.setApplyStatus(ApplicationStatus.SENT);
            } else if (alreadyApplied) {
                vacancy.setAlreadyApplied(true);
                vacancy.setSelected(false);
                vacancy.setStatus("ALREADY_APPLIED");
                vacancy.setCoverLetterStatus(CoverLetterStatus.SKIPPED);
                vacancy.setApplyStatus(ApplicationStatus.ALREADY_APPLIED);
            } else {
                vacancy.setAlreadyApplied(false);
                vacancy.setSelected(true);
                vacancy.setStatus("PENDING");
                vacancy.setCoverLetterStatus(CoverLetterStatus.PENDING);
                vacancy.setApplyStatus(ApplicationStatus.PENDING);
                added++;
            }
            vacancies.save(vacancy);
        }
        List<CampaignVacancy> current = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(context.campaignId());
        campaign.setTotalVacancies(current.size());
        campaign.setAlreadyCount((int) current.stream()
            .filter(v -> v.isAlreadyApplied() || v.getApplyStatus() == ApplicationStatus.ALREADY_APPLIED)
            .count());
        campaign.setAppliedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SENT).count());
        campaign.setSkippedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SKIPPED).count());
        campaign.setFailedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.FAILED
            || v.getApplyStatus() == ApplicationStatus.LETTER_GENERATION_FAILED
            || v.getApplyStatus() == ApplicationStatus.TEST_REQUIRED).count());
        campaign.setGeneratedCount((int) current.stream().filter(v -> v.getCoverLetterStatus() == CoverLetterStatus.GENERATED || v.getCoverLetterStatus() == CoverLetterStatus.EDITED).count());
        campaign.setStatus(CampaignStatus.PREVIEW_READY);
        campaigns.save(campaign);
        LOGGER.info(
            "Campaign preview processed: campaignId={} hhAccountId={} resumeId={} found={} alreadyAppliedByHh={} alreadyAppliedByDatabase={} added={}",
            context.campaignId(),
            context.hhAccountId(),
            context.resumeId(),
            found.size(),
            alreadyAppliedByHh,
            alreadyAppliedByDatabase,
            added
        );
    }

    private List<VacancySummaryAdapterResponse> filterAndDedupe(
        List<VacancySummaryAdapterResponse> raw,
        List<String> includeKeywords,
        List<String> excludeKeywords
    ) {
        Map<String, VacancySummaryAdapterResponse> result = new LinkedHashMap<>();
        for (VacancySummaryAdapterResponse vacancy : raw == null ? List.<VacancySummaryAdapterResponse>of() : raw) {
            String haystack = ((vacancy.title() == null ? "" : vacancy.title()) + " " + (vacancy.searchText() == null ? "" : vacancy.searchText()))
                .toLowerCase(Locale.ROOT);
            if (!includeKeywords.isEmpty() && includeKeywords.stream().noneMatch(haystack::contains)) {
                continue;
            }
            if (excludeKeywords.stream().anyMatch(haystack::contains)) {
                continue;
            }
            result.putIfAbsent(vacancy.hhVacancyId(), vacancy);
        }
        return List.copyOf(result.values());
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.toLowerCase(Locale.ROOT).split(",")).stream()
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
    }

    private List<VacancySummaryAdapterResponse> limit(List<VacancySummaryAdapterResponse> vacancies, Integer limit) {
        if (limit == null || limit <= 0 || vacancies.size() <= limit) {
            return vacancies;
        }
        return vacancies.subList(0, limit);
    }

    private record PreviewContext(
        UUID campaignId,
        UUID hhAccountId,
        UUID resumeId,
        String hhResumeId,
        HhSessionAdapterPayload session,
        String searchUrl,
        int pages,
        Integer vacancyLoadLimit,
        List<String> includeKeywords,
        List<String> excludeKeywords
    ) {
    }
}
