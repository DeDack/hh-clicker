package com.hhclicker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhclicker.dto.request.UpdateCoverLetterRequest;
import com.hhclicker.dto.response.CampaignResponse;
import com.hhclicker.dto.response.CampaignVacancyResponse;
import com.hhclicker.entity.ApplicationCampaign;
import com.hhclicker.entity.CampaignVacancy;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.Resume;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.request.GenerateCoverLetterAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.GeneratedCoverLetterAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.LoadVacancyAdapterResponse;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import com.hhclicker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class CoverLetterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoverLetterService.class);

    private final ApplicationCampaignRepository campaigns;
    private final CampaignVacancyRepository vacancies;
    private final UserRepository users;
    private final CampaignService campaignService;
    private final CoverLetterPermissionService permissionService;
    private final HhAccountService accounts;
    private final HhAdapterClient adapterClient;
    private final TaskExecutor campaignTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public CoverLetterService(
        ApplicationCampaignRepository campaigns,
        CampaignVacancyRepository vacancies,
        UserRepository users,
        CampaignService campaignService,
        CoverLetterPermissionService permissionService,
        HhAccountService accounts,
        HhAdapterClient adapterClient,
        TaskExecutor campaignTaskExecutor,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper
    ) {
        this.campaigns = campaigns;
        this.vacancies = vacancies;
        this.users = users;
        this.campaignService = campaignService;
        this.permissionService = permissionService;
        this.accounts = accounts;
        this.adapterClient = adapterClient;
        this.campaignTaskExecutor = campaignTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CampaignResponse startMassGeneration(UUID userId, UUID campaignId) {
        User user = requireUser(userId);
        permissionService.checkGenerationAllowed(user);
        ApplicationCampaign campaign = campaignService.requireOwned(userId, campaignId);
        if (campaign.getStatus() == CampaignStatus.LETTERS_GENERATING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", "Письма уже генерируются");
        }
        if (campaign.getStatus() == CampaignStatus.PREVIEW_LOADING
            || campaign.getStatus() == CampaignStatus.APPLYING
            || campaign.getStatus() == CampaignStatus.STOPPING) {
            throw new BusinessException("CAMPAIGN_ALREADY_RUNNING", "Кампания уже выполняется");
        }
        campaign.setStatus(CampaignStatus.LETTERS_GENERATING);
        campaign.setStopRequested(false);
        ApplicationCampaign saved = campaigns.save(campaign);
        campaignTaskExecutor.execute(() -> generateSelected(saved.getId(), userId));
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignVacancyResponse regenerate(UUID userId, UUID campaignId, UUID vacancyId) {
        User user = requireUser(userId);
        permissionService.checkGenerationAllowed(user);
        campaignService.requireOwned(userId, campaignId);
        CampaignVacancy vacancy = requireVacancy(campaignId, vacancyId);
        generateForVacancy(vacancy.getId(), true);
        return CampaignVacancyResponse.from(vacancies.findById(vacancyId).orElseThrow());
    }

    @Transactional
    public CampaignVacancyResponse updateManual(UUID userId, UUID campaignId, UUID vacancyId, UpdateCoverLetterRequest request) {
        campaignService.requireOwned(userId, campaignId);
        CampaignVacancy vacancy = requireVacancy(campaignId, vacancyId);
        vacancy.setCoverLetter(request.coverLetter() == null ? "" : request.coverLetter().strip());
        vacancy.setCoverLetterEditedManually(true);
        vacancy.setCoverLetterStatus(CoverLetterStatus.EDITED);
        vacancy.setGenerationError(null);
        return CampaignVacancyResponse.from(vacancies.save(vacancy));
    }

    private void generateSelected(UUID campaignId, UUID userId) {
        List<UUID> vacancyIds = transactionTemplate.execute(status ->
            vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
                .filter(CampaignVacancy::isSelected)
                .filter(v -> !v.isCoverLetterEditedManually())
                .map(CampaignVacancy::getId)
                .toList()
        );
        for (UUID vacancyId : vacancyIds == null ? List.<UUID>of() : vacancyIds) {
            if (stopRequested(campaignId)) {
                finishGeneration(campaignId, CampaignStatus.STOPPED);
                return;
            }
            try {
                generateForVacancy(vacancyId, false);
            } catch (Exception ex) {
                LOGGER.warn("cover_letter_generation failed vacancy={} error={}", vacancyId, ex.getMessage());
            }
            refreshGenerationCounters(campaignId, false);
        }
        refreshGenerationCounters(campaignId, true);
    }

    private boolean stopRequested(UUID campaignId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> campaigns.findById(campaignId)
            .map(ApplicationCampaign::isStopRequested)
            .orElse(true)));
    }

    private void finishGeneration(UUID campaignId, CampaignStatus status) {
        transactionTemplate.executeWithoutResult(tx -> campaigns.findById(campaignId).ifPresent(campaign -> {
            campaign.setStatus(status);
            campaigns.save(campaign);
        }));
    }

    public CampaignVacancy generateForVacancy(UUID vacancyId, boolean force) {
        GenerationContext context = transactionTemplate.execute(status -> {
            CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
            if (!vacancy.isSelected()) {
                return null;
            }
            if (!force && vacancy.isCoverLetterEditedManually()) {
                return null;
            }
            vacancy.setCoverLetterStatus(CoverLetterStatus.GENERATING);
            vacancy.setGenerationError(null);
            vacancies.save(vacancy);
            ApplicationCampaign campaign = vacancy.getCampaign();
            HhAccount account = campaign.getHhAccount();
            HhSessionAdapterPayload session = accounts.decryptSession(account);
            Resume resume = campaign.getResume();
            return new GenerationContext(
                vacancy.getId(),
                value(resume.getTitle()),
                value(resume.getText()),
                value(resume.getContentHash()),
                value(resume.getCandidateProfile()),
                resume.getGender().name(),
                value(resume.getTelegramUsername()),
                value(vacancy.getHhVacancyId()),
                value(vacancy.getTitle()),
                value(vacancy.getCompanyName()),
                value(vacancy.getDescription()),
                session
            );
        });
        if (context == null) {
            return vacancies.findById(vacancyId).orElseThrow();
        }
        try {
            String vacancyTitle = context.vacancyTitle();
            String companyName = context.companyName();
            String description = context.description();
            String descriptionHash = "";
            if (description.isBlank()) {
                LoadVacancyAdapterResponse loaded = adapterClient.loadVacancy(context.session(), context.hhVacancyId(), context.vacancyTitle());
                transactionTemplate.executeWithoutResult(status -> {
                    CampaignVacancy current = vacancies.findById(context.vacancyId()).orElseThrow();
                    current.setTitle(loaded.title());
                    current.setCompanyName(loaded.companyName());
                    current.setVacancyUrl(loaded.url());
                    current.setDescription(loaded.description());
                    current.setDescriptionHash(loaded.descriptionHash());
                    vacancies.save(current);
                });
                vacancyTitle = value(loaded.title());
                companyName = value(loaded.companyName());
                description = value(loaded.description());
                descriptionHash = value(loaded.descriptionHash());
            }
            GeneratedCoverLetterAdapterResponse generated = adapterClient.generateCoverLetter(toRequest(context, vacancyTitle, companyName, description, descriptionHash));
            transactionTemplate.executeWithoutResult(status -> saveGenerated(context.vacancyId(), generated));
        } catch (Exception ex) {
            LOGGER.warn("cover_letter_generation failed vacancy={} error={}", context.vacancyId(), ex.getMessage());
            transactionTemplate.executeWithoutResult(status -> {
                CampaignVacancy current = vacancies.findById(context.vacancyId()).orElseThrow();
                current.setCoverLetterStatus(CoverLetterStatus.FAILED);
                current.setGenerationError(ex instanceof BusinessException business ? business.getCode() : "ADAPTER_UNAVAILABLE");
                vacancies.save(current);
            });
        }
        return vacancies.findById(vacancyId).orElseThrow();
    }

    private GenerateCoverLetterAdapterRequest toRequest(
        GenerationContext context,
        String vacancyTitle,
        String companyName,
        String description,
        String descriptionHash
    ) {
        return new GenerateCoverLetterAdapterRequest(
            new GenerateCoverLetterAdapterRequest.ResumePayload(context.resumeTitle(), context.resumeText(), context.resumeHash()),
            context.candidateProfile(),
            context.candidateGender(),
            context.telegramUsername(),
            new GenerateCoverLetterAdapterRequest.VacancyPayload(
                context.hhVacancyId(),
                value(vacancyTitle),
                value(companyName),
                value(description),
                List.of()
            ),
            new GenerateCoverLetterAdapterRequest.SettingsPayload("живой", true, true, 2)
        );
    }

    private String value(String raw) {
        return raw == null ? "" : raw;
    }

    private void saveGenerated(UUID vacancyId, GeneratedCoverLetterAdapterResponse generated) {
        CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
        if ("PROFILE_MISMATCH".equals(generated.status())) {
            vacancy.setCoverLetterStatus(CoverLetterStatus.PROFILE_MISMATCH);
            vacancy.setGenerationError("PROFILE_MISMATCH");
        } else if ("GENERATED".equals(generated.status())) {
            vacancy.setCoverLetter(generated.coverLetter());
            vacancy.setCoverLetterStatus(CoverLetterStatus.GENERATED);
            vacancy.setGenerationError(null);
        } else {
            vacancy.setCoverLetterStatus(CoverLetterStatus.FAILED);
            vacancy.setGenerationError(generated.errorCode() == null ? "LLM_BAD_RESPONSE" : generated.errorCode());
        }
        try {
            vacancy.setMatchAnalysis(objectMapper.writeValueAsString(generated.matchAnalysis()));
        } catch (Exception ignored) {
            vacancy.setMatchAnalysis("{}");
        }
        vacancy.setGenerationProvider(generated.provider());
        vacancy.setGenerationModel(generated.model());
        vacancy.setPromptVersion(generated.promptVersion());
        vacancy.setInputTokens(generated.inputTokens());
        vacancy.setOutputTokens(generated.outputTokens());
        vacancy.setGenerationAttempts(generated.attempts());
        vacancies.save(vacancy);
    }

    private void refreshGenerationCounters(UUID campaignId, boolean finish) {
        transactionTemplate.executeWithoutResult(status -> campaigns.findById(campaignId).ifPresent(campaign -> {
            List<CampaignVacancy> selected = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
                .filter(CampaignVacancy::isSelected)
                .toList();
            long failed = selected.stream()
                .filter(v -> v.getCoverLetterStatus() == CoverLetterStatus.FAILED
                    || v.getCoverLetterStatus() == CoverLetterStatus.PROFILE_MISMATCH)
                .count();
            long ready = selected.stream()
                .filter(v -> v.getCoverLetterStatus() == CoverLetterStatus.GENERATED
                    || v.getCoverLetterStatus() == CoverLetterStatus.EDITED)
                .count();
            campaign.setGeneratedCount((int) ready);
            campaign.setFailedCount((int) failed);
            if (finish) {
                campaign.setStatus(campaign.isStopRequested() ? CampaignStatus.STOPPED : CampaignStatus.READY_TO_APPLY);
            }
            campaigns.save(campaign);
        }));
    }

    private CampaignVacancy requireVacancy(UUID campaignId, UUID vacancyId) {
        return vacancies.findByIdAndCampaignId(vacancyId, campaignId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Вакансия кампании не найдена"));
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден"));
    }

    private record GenerationContext(
        UUID vacancyId,
        String resumeTitle,
        String resumeText,
        String resumeHash,
        String candidateProfile,
        String candidateGender,
        String telegramUsername,
        String hhVacancyId,
        String vacancyTitle,
        String companyName,
        String description,
        HhSessionAdapterPayload session
    ) {
    }
}
