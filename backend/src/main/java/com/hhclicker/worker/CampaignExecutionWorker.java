package com.hhclicker.worker;

import com.hhclicker.entity.ApplicationCampaign;
import com.hhclicker.entity.ApplicationAttempt;
import com.hhclicker.entity.CampaignVacancy;
import com.hhclicker.enumeration.ApplicationStatus;
import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.request.ApplyAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.ApplyAdapterResponse;
import com.hhclicker.repository.ApplicationAttemptRepository;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import com.hhclicker.service.CoverLetterService;
import com.hhclicker.service.HhAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class CampaignExecutionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignExecutionWorker.class);

    private final TransactionTemplate transactionTemplate;
    private final ApplicationCampaignRepository campaigns;
    private final CampaignVacancyRepository vacancies;
    private final ApplicationAttemptRepository attempts;
    private final HhAccountService accounts;
    private final HhAdapterClient adapterClient;
    private final CoverLetterService coverLetters;

    public CampaignExecutionWorker(
        TransactionTemplate transactionTemplate,
        ApplicationCampaignRepository campaigns,
        CampaignVacancyRepository vacancies,
        ApplicationAttemptRepository attempts,
        HhAccountService accounts,
        HhAdapterClient adapterClient,
        CoverLetterService coverLetters
    ) {
        this.transactionTemplate = transactionTemplate;
        this.campaigns = campaigns;
        this.vacancies = vacancies;
        this.attempts = attempts;
        this.accounts = accounts;
        this.adapterClient = adapterClient;
        this.coverLetters = coverLetters;
    }

    public void run(UUID campaignId) {
        WorkerContext context = transactionTemplate.execute(status -> {
            ApplicationCampaign campaign = campaigns.findById(campaignId).orElseThrow();
            return new WorkerContext(
                campaign.getId(),
                campaign.getHhAccount().getId(),
                campaign.getResume().getId(),
                campaign.getResume().getHhResumeId(),
                accounts.decryptSession(campaign.getHhAccount()),
                campaign.getCoverLetterMode(),
                campaign.getCommonCoverLetter(),
                campaign.isReviewCoverLettersBeforeApply(),
                campaign.getDelaySeconds(),
                campaign.getMaxApplications()
            );
        });
        int sentOrAlready = 0;
        List<UUID> ids = transactionTemplate.execute(status -> vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaignId).stream()
            .filter(CampaignVacancy::isSelected)
            .filter(v -> !v.isAlreadyApplied())
            .map(CampaignVacancy::getId)
            .toList());
        for (UUID vacancyId : ids == null ? List.<UUID>of() : ids) {
            if (context.maxApplications() > 0 && sentOrAlready >= context.maxApplications()) {
                break;
            }
            if (stopRequested(campaignId)) {
                finish(campaignId, CampaignStatus.STOPPED);
                return;
            }
            ApplyJob job = prepareAttempt(vacancyId, context);
            if (job == null) {
                continue;
            }
            ApplyAdapterResponse response;
            try {
                response = adapterClient.apply(new ApplyAdapterRequest(context.session(), context.resumeId(), job.hhVacancyId(), job.coverLetter()));
            } catch (Exception ex) {
                response = new ApplyAdapterResponse("FAILED", null, null, null, "ADAPTER_UNAVAILABLE");
            }
            ApplicationStatus status = mapStatus(response.status());
            saveApplyResult(job.attemptId(), vacancyId, status, response);
            if (status == ApplicationStatus.SENT || status == ApplicationStatus.ALREADY_APPLIED) {
                sentOrAlready++;
            }
            if (status == ApplicationStatus.AUTH_ERROR || status == ApplicationStatus.LIMIT_EXCEEDED) {
                finish(campaignId, CampaignStatus.STOPPED);
                return;
            }
            sleep(context.delaySeconds());
        }
        finish(campaignId, CampaignStatus.COMPLETED);
    }

    private ApplyJob prepareAttempt(UUID vacancyId, WorkerContext context) {
        if (skipIfAlreadyApplied(vacancyId, context)) {
            return null;
        }
        if ("PERSONAL_AI".equals(context.coverLetterMode()) && !context.reviewCoverLettersBeforeApply()) {
            CampaignVacancy generated = coverLetters.generateForVacancy(vacancyId, false);
            if (!(generated.getCoverLetterStatus() == CoverLetterStatus.GENERATED || generated.getCoverLetterStatus() == CoverLetterStatus.EDITED)) {
                markLetterGenerationFailed(vacancyId, generated.getGenerationError());
                if (isGlobalLlmError(generated.getGenerationError())) {
                    finish(context.campaignId(), CampaignStatus.STOPPED);
                }
                return null;
            }
        }
        return transactionTemplate.execute(status -> {
            CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
            if (!vacancy.isSelected() || vacancy.getApplyStatus() == ApplicationStatus.SENT) {
                return null;
            }
            if (vacancy.isAlreadyApplied()
                || vacancy.getApplyStatus() == ApplicationStatus.ALREADY_APPLIED
                || vacancies.existsSuccessfulApplication(
                    context.hhAccountId(),
                    context.resumeUuid(),
                    vacancy.getHhVacancyId(),
                    List.of(ApplicationStatus.SENT, ApplicationStatus.ALREADY_APPLIED)
                )) {
                markAlreadyApplied(vacancy);
                updateCounters(vacancy.getCampaign());
                return null;
            }
            String letter = switch (context.coverLetterMode()) {
                case "COMMON" -> context.commonCoverLetter();
                case "NONE" -> "";
                default -> vacancy.getCoverLetter();
            };
            if ("PERSONAL_AI".equals(context.coverLetterMode()) && context.reviewCoverLettersBeforeApply()
                && !(vacancy.getCoverLetterStatus() == CoverLetterStatus.GENERATED || vacancy.getCoverLetterStatus() == CoverLetterStatus.EDITED)) {
                vacancy.setApplyStatus(ApplicationStatus.SKIPPED);
                vacancy.setApplyErrorCode("COVER_LETTERS_NOT_READY");
                vacancies.save(vacancy);
                return null;
            }
            vacancy.setApplyStatus(ApplicationStatus.SENDING);
            vacancies.save(vacancy);
            ApplicationAttempt attempt = new ApplicationAttempt();
            attempt.setCampaignVacancy(vacancy);
            attempt.setAttemptNumber((int) attempts.countByCampaignVacancyId(vacancyId) + 1);
            attempt.setStatus(ApplicationStatus.SENDING);
            attempt = attempts.save(attempt);
            return new ApplyJob(attempt.getId(), vacancy.getHhVacancyId(), letter == null ? "" : letter);
        });
    }

    private boolean skipIfAlreadyApplied(UUID vacancyId, WorkerContext context) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
            if (!vacancy.isSelected() || vacancy.getApplyStatus() == ApplicationStatus.SENT) {
                return true;
            }
            if (vacancy.isAlreadyApplied()
                || vacancy.getApplyStatus() == ApplicationStatus.ALREADY_APPLIED
                || vacancies.existsSuccessfulApplication(
                    context.hhAccountId(),
                    context.resumeUuid(),
                    vacancy.getHhVacancyId(),
                    List.of(ApplicationStatus.SENT, ApplicationStatus.ALREADY_APPLIED)
                )) {
                markAlreadyApplied(vacancy);
                vacancies.save(vacancy);
                updateCounters(vacancy.getCampaign());
                return true;
            }
            return false;
        }));
    }

    private void markLetterGenerationFailed(UUID vacancyId, String code) {
        transactionTemplate.executeWithoutResult(status -> {
            CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
            vacancy.setApplyStatus(ApplicationStatus.LETTER_GENERATION_FAILED);
            vacancy.setApplyErrorCode(code == null ? "LETTER_GENERATION_FAILED" : code);
            vacancies.save(vacancy);
            updateCounters(vacancy.getCampaign());
        });
    }

    private void saveApplyResult(UUID attemptId, UUID vacancyId, ApplicationStatus status, ApplyAdapterResponse response) {
        transactionTemplate.executeWithoutResult(tx -> {
            ApplicationAttempt attempt = attempts.findById(attemptId).orElseThrow();
            attempt.setStatus(status);
            attempt.setHttpStatus(response.httpStatus());
            attempt.setErrorCode(response.errorCode());
            attempt.setTopicId(response.topicId());
            attempts.save(attempt);
            CampaignVacancy vacancy = vacancies.findById(vacancyId).orElseThrow();
            vacancy.setApplyStatus(status);
            vacancy.setApplyErrorCode(response.errorCode());
            if (status == ApplicationStatus.ALREADY_APPLIED) {
                markAlreadyApplied(vacancy);
            }
            vacancies.save(vacancy);
            updateCounters(vacancy.getCampaign());
        });
    }

    private void markAlreadyApplied(CampaignVacancy vacancy) {
        vacancy.setAlreadyApplied(true);
        vacancy.setSelected(false);
        vacancy.setStatus("ALREADY_APPLIED");
        vacancy.setApplyStatus(ApplicationStatus.ALREADY_APPLIED);
        vacancy.setApplyErrorCode(null);
        vacancy.setCoverLetterStatus(CoverLetterStatus.SKIPPED);
    }

    private void updateCounters(ApplicationCampaign campaign) {
        List<CampaignVacancy> all = vacancies.findAllByCampaignIdOrderBySourcePageAscTitleAsc(campaign.getId());
        campaign.setAppliedCount((int) all.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SENT).count());
        campaign.setAlreadyCount((int) all.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.ALREADY_APPLIED).count());
        campaign.setSkippedCount((int) all.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.SKIPPED).count());
        campaign.setFailedCount((int) all.stream().filter(v -> v.getApplyStatus() == ApplicationStatus.FAILED
            || v.getApplyStatus() == ApplicationStatus.LETTER_GENERATION_FAILED
            || v.getApplyStatus() == ApplicationStatus.TEST_REQUIRED).count());
        campaigns.save(campaign);
    }

    private boolean stopRequested(UUID campaignId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> campaigns.findById(campaignId)
            .map(ApplicationCampaign::isStopRequested)
            .orElse(true)));
    }

    private void finish(UUID campaignId, CampaignStatus status) {
        transactionTemplate.executeWithoutResult(tx -> campaigns.findById(campaignId).ifPresent(campaign -> {
            campaign.setStatus(status);
            campaign.setFinishedAt(Instant.now());
            campaigns.save(campaign);
        }));
    }

    private ApplicationStatus mapStatus(String raw) {
        try {
            return ApplicationStatus.valueOf(raw == null ? "FAILED" : raw);
        } catch (IllegalArgumentException ex) {
            return ApplicationStatus.FAILED;
        }
    }

    private String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PERSONAL_AI";
        }
        return "personal".equalsIgnoreCase(raw) ? "PERSONAL_AI" : raw.strip().toUpperCase();
    }

    private boolean isGlobalLlmError(String code) {
        return "LLM_UNAUTHORIZED".equals(code) || "LLM_NOT_CONFIGURED".equals(code) || "LLM_RATE_LIMITED".equals(code);
    }

    private void sleep(BigDecimal seconds) {
        try {
            long millis = seconds == null ? 0 : seconds.multiply(BigDecimal.valueOf(1000)).longValue();
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("campaign_worker interrupted");
        }
    }

    private record WorkerContext(
        UUID campaignId,
        UUID hhAccountId,
        UUID resumeUuid,
        String resumeId,
        HhSessionAdapterPayload session,
        String coverLetterMode,
        String commonCoverLetter,
        boolean reviewCoverLettersBeforeApply,
        BigDecimal delaySeconds,
        int maxApplications
    ) {
        private WorkerContext {
            coverLetterMode = coverLetterMode == null || coverLetterMode.isBlank()
                ? "PERSONAL_AI"
                : ("personal".equalsIgnoreCase(coverLetterMode) ? "PERSONAL_AI" : coverLetterMode.strip().toUpperCase());
        }
    }

    private record ApplyJob(UUID attemptId, String hhVacancyId, String coverLetter) {
    }
}
