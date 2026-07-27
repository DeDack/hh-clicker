package com.hhclicker.service;

import com.hhclicker.dto.request.CreateCampaignRequest;
import com.hhclicker.dto.request.UpdateCampaignSettingsRequest;
import com.hhclicker.dto.request.UpdateCampaignVacancyRequest;
import com.hhclicker.dto.response.CampaignDetailsResponse;
import com.hhclicker.dto.response.CampaignResponse;
import com.hhclicker.dto.response.CampaignVacancyResponse;
import com.hhclicker.entity.ApplicationCampaign;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.Resume;
import com.hhclicker.entity.SavedSearch;
import com.hhclicker.enumeration.ApplicationStatus;
import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.ApplicationAttemptRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import com.hhclicker.repository.ResumeRepository;
import com.hhclicker.repository.SavedSearchRepository;
import com.hhclicker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class CampaignService {
    private final ApplicationCampaignRepository campaigns;
    private final CampaignVacancyRepository vacancies;
    private final ResumeRepository resumes;
    private final SavedSearchRepository savedSearches;
    private final UserRepository users;
    private final ApplicationAttemptRepository attempts;
    private final HhAccountService accounts;
    private final CampaignPreviewService previewService;

    public CampaignService(
        ApplicationCampaignRepository campaigns,
        CampaignVacancyRepository vacancies,
        ResumeRepository resumes,
        SavedSearchRepository savedSearches,
        UserRepository users,
        ApplicationAttemptRepository attempts,
        HhAccountService accounts,
        CampaignPreviewService previewService
    ) {
        this.campaigns = campaigns;
        this.vacancies = vacancies;
        this.resumes = resumes;
        this.savedSearches = savedSearches;
        this.users = users;
        this.attempts = attempts;
        this.accounts = accounts;
        this.previewService = previewService;
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> list(UUID userId) {
        return campaigns.findAllByUserId(userId).stream().map(CampaignResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetailsResponse get(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        return new CampaignDetailsResponse(CampaignResponse.from(campaign), listVacancies(userId, campaignId));
    }

    @Transactional
    public CampaignResponse create(UUID userId, CreateCampaignRequest request) {
        SavedSearch savedSearch = null;
        HhAccount account;
        Resume resume;
        String searchUrl;
        int pages;
        String includeKeywords;
        String excludeKeywords;
        Integer vacancyLoadLimit;
        String coverLetterMode;
        String commonCoverLetter;
        BigDecimal delaySeconds;
        int maxApplications;
        boolean reviewCoverLettersBeforeApply;
        if (request.savedSearchId() != null) {
            savedSearch = savedSearches.findByIdAndUserId(request.savedSearchId(), userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Сохранённый поиск не найден"));
            account = savedSearch.getHhAccount();
            resume = savedSearch.getResume();
            CreateCampaignRequest.Overrides overrides = request.overrides();
            searchUrl = savedSearch.getSearchUrl();
            pages = overrides != null && overrides.pages() != null ? overrides.pages() : savedSearch.getPages();
            vacancyLoadLimit = overrides != null && overrides.vacancyLoadLimit() != null ? overrides.vacancyLoadLimit() : savedSearch.getVacancyLoadLimit();
            includeKeywords = overrides != null && overrides.includeKeywords() != null ? overrides.includeKeywords() : savedSearch.getIncludeKeywords();
            excludeKeywords = overrides != null && overrides.excludeKeywords() != null ? overrides.excludeKeywords() : savedSearch.getExcludeKeywords();
            coverLetterMode = overrides != null && overrides.coverLetterMode() != null ? overrides.coverLetterMode() : savedSearch.getDefaultCoverLetterMode();
            commonCoverLetter = overrides != null && overrides.commonCoverLetter() != null ? overrides.commonCoverLetter() : savedSearch.getDefaultCommonCoverLetter();
            delaySeconds = overrides != null && overrides.delaySeconds() != null ? overrides.delaySeconds() : savedSearch.getDefaultDelaySeconds();
            maxApplications = overrides != null && overrides.maxApplications() != null ? overrides.maxApplications() : savedSearch.getDefaultMaxApplications();
            reviewCoverLettersBeforeApply = overrides == null || overrides.reviewCoverLettersBeforeApply() == null || overrides.reviewCoverLettersBeforeApply();
        } else {
            if (request.hhAccountId() == null || request.resumeId() == null) {
                throw new BusinessException("VALIDATION_ERROR", "Выберите HH-аккаунт и резюме");
            }
            account = accounts.requireOwned(userId, request.hhAccountId());
            resume = resumes.findByIdAndUserId(request.resumeId(), userId)
                .filter(r -> r.getHhAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Резюме должно принадлежать выбранному HH-аккаунту"));
            searchUrl = request.searchUrl();
            pages = request.pages() == null ? 50 : request.pages();
            vacancyLoadLimit = request.vacancyLoadLimit();
            includeKeywords = request.includeKeywords();
            excludeKeywords = request.excludeKeywords();
            coverLetterMode = request.coverLetterMode();
            commonCoverLetter = request.commonCoverLetter();
            delaySeconds = request.delaySeconds();
            maxApplications = request.maxApplications() == null ? 0 : request.maxApplications();
            reviewCoverLettersBeforeApply = request.reviewCoverLettersBeforeApply() == null || request.reviewCoverLettersBeforeApply();
        }
        validateSearchUrl(searchUrl);
        ApplicationCampaign campaign = new ApplicationCampaign();
        campaign.setUser(users.findById(userId).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден")));
        campaign.setHhAccount(account);
        campaign.setResume(resume);
        campaign.setSavedSearch(savedSearch);
        campaign.setName(request.name().strip());
        campaign.setSearchUrl(searchUrl.strip());
        campaign.setPages(Math.max(1, Math.min(pages, 50)));
        campaign.setVacancyLoadLimit(normalizeLimit(vacancyLoadLimit));
        campaign.setIncludeKeywords(blankToNull(includeKeywords));
        campaign.setExcludeKeywords(blankToNull(excludeKeywords));
        campaign.setCoverLetterMode(normalizeCoverLetterMode(coverLetterMode));
        campaign.setCommonCoverLetter(blankToNull(commonCoverLetter));
        campaign.setReviewCoverLettersBeforeApply(reviewCoverLettersBeforeApply);
        campaign.setDelaySeconds(delaySeconds == null ? BigDecimal.ONE : delaySeconds);
        campaign.setMaxApplications(Math.max(0, maxApplications));
        campaign.setStatus(CampaignStatus.DRAFT);
        return CampaignResponse.from(campaigns.save(campaign));
    }

    @Transactional
    public void delete(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        ensureNotRunning(campaign, "Выполняющуюся кампанию нельзя удалить. Сначала остановите её.");
        List<UUID> vacancyIds = vacancies.findIdsByCampaignId(campaignId);
        if (!vacancyIds.isEmpty()) {
            attempts.deleteAllByCampaignVacancyIds(vacancyIds);
            vacancies.deleteAllByIdIn(vacancyIds);
        }
        campaigns.delete(campaign);
    }

    @Transactional
    public CampaignResponse updateSettings(UUID userId, UUID campaignId, UpdateCampaignSettingsRequest request) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        if (campaign.getStatus() == CampaignStatus.APPLYING || campaign.getStatus() == CampaignStatus.STOPPING
            || campaign.getStatus() == CampaignStatus.LETTERS_GENERATING || campaign.getStatus() == CampaignStatus.PREVIEW_LOADING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", "Настройки нельзя менять во время выполнения кампании");
        }
        if (request.coverLetterMode() != null && !request.coverLetterMode().isBlank()) {
            campaign.setCoverLetterMode(normalizeCoverLetterMode(request.coverLetterMode()));
        }
        if (request.commonCoverLetter() != null) {
            campaign.setCommonCoverLetter(request.commonCoverLetter().strip());
        }
        if (request.reviewCoverLettersBeforeApply() != null) {
            campaign.setReviewCoverLettersBeforeApply(request.reviewCoverLettersBeforeApply());
        }
        if (request.delaySeconds() != null) {
            if (request.delaySeconds().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("VALIDATION_ERROR", "Задержка не может быть отрицательной");
            }
            campaign.setDelaySeconds(request.delaySeconds());
        }
        if (request.maxApplications() != null) {
            campaign.setMaxApplications(Math.max(0, request.maxApplications()));
        }
        return CampaignResponse.from(campaigns.save(campaign));
    }

    @Transactional
    public CampaignResponse startPreview(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        if (campaign.getStatus() == CampaignStatus.PREVIEW_LOADING || campaign.getStatus() == CampaignStatus.APPLYING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", "Кампания уже выполняется");
        }
        campaign.setStatus(CampaignStatus.PREVIEW_LOADING);
        campaign.setFailedCount(0);
        ApplicationCampaign saved = campaigns.save(campaign);
        previewService.start(saved.getId());
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse reloadPreview(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        ensureNotRunning(campaign, "Повторную загрузку нельзя запустить во время выполнения кампании");
        refreshSearchSnapshotFromSavedSearch(userId, campaign);
        clearReloadableVacancies(campaignId);
        refreshCounters(campaign);
        campaign.setStopRequested(false);
        campaign.setStatus(CampaignStatus.PREVIEW_LOADING);
        campaign.setFailedCount(0);
        ApplicationCampaign saved = campaigns.save(campaign);
        previewService.start(saved.getId());
        return CampaignResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignVacancyResponse> listVacancies(UUID userId, UUID campaignId) {
        requireOwned(userId, campaignId);
        return vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
            .map(CampaignVacancyResponse::from)
            .toList();
    }

    @Transactional
    public CampaignVacancyResponse updateVacancy(UUID userId, UUID campaignId, UUID vacancyId, UpdateCampaignVacancyRequest request) {
        requireOwned(userId, campaignId);
        var vacancy = vacancies.findByIdAndCampaignId(vacancyId, campaignId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Вакансия кампании не найдена"));
        if (!request.selected() && vacancy.getApplyStatus() == com.hhclicker.enumeration.ApplicationStatus.SENT) {
            throw new BusinessException("VALIDATION_ERROR", "Уже отправленную вакансию нельзя исключить");
        }
        if (request.selected() && vacancy.isAlreadyApplied()) {
            throw new BusinessException("VALIDATION_ERROR", "На эту вакансию уже откликались этим резюме");
        }
        vacancy.setSelected(request.selected());
        if (request.selected()) {
            vacancy.setStatus("PENDING");
            if (vacancy.getApplyStatus() == com.hhclicker.enumeration.ApplicationStatus.SKIPPED) {
                vacancy.setApplyStatus(com.hhclicker.enumeration.ApplicationStatus.PENDING);
            }
            if (vacancy.getCoverLetterStatus() == com.hhclicker.enumeration.CoverLetterStatus.SKIPPED) {
                vacancy.setCoverLetterStatus(com.hhclicker.enumeration.CoverLetterStatus.PENDING);
            }
        } else {
            if (vacancy.isAlreadyApplied()) {
                vacancy.setStatus("ALREADY_APPLIED");
                vacancy.setApplyStatus(com.hhclicker.enumeration.ApplicationStatus.ALREADY_APPLIED);
                vacancy.setCoverLetterStatus(com.hhclicker.enumeration.CoverLetterStatus.SKIPPED);
            } else {
                vacancy.setStatus("SKIPPED");
                vacancy.setApplyStatus(com.hhclicker.enumeration.ApplicationStatus.SKIPPED);
                vacancy.setCoverLetterStatus(com.hhclicker.enumeration.CoverLetterStatus.SKIPPED);
            }
        }
        return CampaignVacancyResponse.from(vacancies.save(vacancy));
    }

    @Transactional
    public List<CampaignVacancyResponse> excludeProfileMismatches(UUID userId, UUID campaignId) {
        ApplicationCampaign campaign = requireOwned(userId, campaignId);
        ensureNotRunning(campaign, "Нельзя менять выборку во время выполнения кампании");
        List<com.hhclicker.entity.CampaignVacancy> updated = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
            .filter(vacancy -> vacancy.isSelected())
            .filter(vacancy -> !vacancy.isAlreadyApplied())
            .filter(vacancy -> vacancy.getApplyStatus() != ApplicationStatus.SENT)
            .filter(vacancy -> vacancy.getCoverLetterStatus() == CoverLetterStatus.PROFILE_MISMATCH)
            .peek(vacancy -> {
                vacancy.setSelected(false);
                vacancy.setStatus("SKIPPED");
                vacancy.setApplyStatus(ApplicationStatus.SKIPPED);
            })
            .toList();
        vacancies.saveAll(updated);
        return vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
            .map(CampaignVacancyResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationCampaign requireOwned(UUID userId, UUID campaignId) {
        return campaigns.findByIdAndUserId(campaignId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Кампания не найдена"));
    }

    private void validateSearchUrl(String raw) {
        try {
            URI uri = URI.create(raw.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!"https".equals(uri.getScheme()) || !("hh.ru".equals(host) || host.endsWith(".hh.ru")) || !"/search/vacancy".equals(uri.getPath())) {
                throw new BusinessException("VALIDATION_ERROR", "URL поиска должен вести на https://hh.ru/search/vacancy");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Некорректный URL поиска");
        }
    }

    private void ensureNotRunning(ApplicationCampaign campaign, String message) {
        if (campaign.getStatus() == CampaignStatus.APPLYING
            || campaign.getStatus() == CampaignStatus.STOPPING
            || campaign.getStatus() == CampaignStatus.LETTERS_GENERATING
            || campaign.getStatus() == CampaignStatus.PREVIEW_LOADING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", message);
        }
    }

    private void refreshSearchSnapshotFromSavedSearch(UUID userId, ApplicationCampaign campaign) {
        if (campaign.getSavedSearch() == null) {
            return;
        }
        SavedSearch savedSearch = savedSearches.findByIdAndUserId(campaign.getSavedSearch().getId(), userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Сохранённый поиск не найден"));
        if (!savedSearch.getHhAccount().getId().equals(campaign.getHhAccount().getId())
            || !savedSearch.getResume().getId().equals(campaign.getResume().getId())) {
            throw new BusinessException("VALIDATION_ERROR", "Сохранённый поиск теперь привязан к другому HH-аккаунту или резюме. Создайте новую кампанию.");
        }
        validateSearchUrl(savedSearch.getSearchUrl());
        campaign.setSearchUrl(savedSearch.getSearchUrl().strip());
        campaign.setPages(Math.max(1, Math.min(savedSearch.getPages(), 50)));
        campaign.setVacancyLoadLimit(normalizeLimit(savedSearch.getVacancyLoadLimit()));
        campaign.setIncludeKeywords(blankToNull(savedSearch.getIncludeKeywords()));
        campaign.setExcludeKeywords(blankToNull(savedSearch.getExcludeKeywords()));
    }

    private void clearReloadableVacancies(UUID campaignId) {
        List<UUID> reloadableIds = vacancies.findIdsByCampaignIdAndApplyStatusNotIn(
            campaignId,
            List.of(ApplicationStatus.SENT, ApplicationStatus.ALREADY_APPLIED)
        );
        if (reloadableIds.isEmpty()) {
            return;
        }
        attempts.deleteAllByCampaignVacancyIds(reloadableIds);
        vacancies.deleteAllByIdIn(reloadableIds);
    }

    private void refreshCounters(ApplicationCampaign campaign) {
        var current = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaign.getId());
        campaign.setTotalVacancies(current.size());
        campaign.setAlreadyCount((int) current.stream().filter(v -> v.isAlreadyApplied() || v.getApplyStatus() == ApplicationStatus.ALREADY_APPLIED).count());
        campaign.setAppliedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SENT).count());
        campaign.setSkippedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SKIPPED).count());
        campaign.setFailedCount((int) current.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.FAILED
            || v.getApplyStatus() == ApplicationStatus.LETTER_GENERATION_FAILED
            || v.getApplyStatus() == ApplicationStatus.TEST_REQUIRED).count());
        campaign.setGeneratedCount((int) current.stream().filter(v -> v.getCoverLetterStatus() == CoverLetterStatus.GENERATED || v.getCoverLetterStatus() == CoverLetterStatus.EDITED).count());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Integer normalizeLimit(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return Math.min(value, 10_000);
    }

    private String normalizeCoverLetterMode(String raw) {
        String mode = blankToNull(raw);
        if (mode == null) {
            return "PERSONAL_AI";
        }
        return switch (mode.strip().toUpperCase()) {
            case "PERSONAL", "PERSONAL_AI" -> "PERSONAL_AI";
            case "COMMON" -> "COMMON";
            case "NONE" -> "NONE";
            default -> throw new BusinessException("VALIDATION_ERROR", "Режим письма должен быть NONE, COMMON или PERSONAL_AI");
        };
    }
}
