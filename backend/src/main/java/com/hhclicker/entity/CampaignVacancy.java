package com.hhclicker.entity;

import com.hhclicker.enumeration.ApplicationStatus;
import com.hhclicker.enumeration.CoverLetterStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_vacancies", uniqueConstraints = @UniqueConstraint(name = "uq_campaign_vacancies_campaign_hh_id", columnNames = {"campaign_id", "hh_vacancy_id"}))
public class CampaignVacancy {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private ApplicationCampaign campaign;

    @Column(name = "hh_vacancy_id", nullable = false, length = 64)
    private String hhVacancyId;

    @Column(name = "hh_account_id", nullable = false)
    private UUID hhAccountId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(nullable = false, length = 320)
    private String title;

    @Column(name = "company_name", length = 240)
    private String companyName;

    @Column(name = "vacancy_url", nullable = false)
    private String vacancyUrl;

    @Column
    private String description;

    @Column(name = "description_hash", length = 64)
    private String descriptionHash;

    @Column(name = "source_page", nullable = false)
    private int sourcePage;

    @Column(nullable = false)
    private boolean selected = true;

    @Column(name = "already_applied", nullable = false)
    private boolean alreadyApplied;

    @Column(nullable = false, length = 40)
    private String status = "PENDING";

    @Column(name = "match_analysis", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String matchAnalysis;

    @Column(name = "cover_letter")
    private String coverLetter;

    @Enumerated(EnumType.STRING)
    @Column(name = "cover_letter_status", nullable = false, length = 40)
    private CoverLetterStatus coverLetterStatus = CoverLetterStatus.PENDING;

    @Column(name = "cover_letter_edited_manually", nullable = false)
    private boolean coverLetterEditedManually;

    @Column(name = "generation_provider", length = 64)
    private String generationProvider;

    @Column(name = "generation_model", length = 120)
    private String generationModel;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "generation_attempts", nullable = false)
    private int generationAttempts;

    @Column(name = "generation_error")
    private String generationError;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_status", nullable = false, length = 40)
    private ApplicationStatus applyStatus = ApplicationStatus.PENDING;

    @Column(name = "apply_error_code", length = 80)
    private String applyErrorCode;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ApplicationCampaign getCampaign() { return campaign; }
    public void setCampaign(ApplicationCampaign campaign) { this.campaign = campaign; }
    public String getHhVacancyId() { return hhVacancyId; }
    public void setHhVacancyId(String hhVacancyId) { this.hhVacancyId = hhVacancyId; }
    public UUID getHhAccountId() { return hhAccountId; }
    public void setHhAccountId(UUID hhAccountId) { this.hhAccountId = hhAccountId; }
    public UUID getResumeId() { return resumeId; }
    public void setResumeId(UUID resumeId) { this.resumeId = resumeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getVacancyUrl() { return vacancyUrl; }
    public void setVacancyUrl(String vacancyUrl) { this.vacancyUrl = vacancyUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDescriptionHash() { return descriptionHash; }
    public void setDescriptionHash(String descriptionHash) { this.descriptionHash = descriptionHash; }
    public int getSourcePage() { return sourcePage; }
    public void setSourcePage(int sourcePage) { this.sourcePage = sourcePage; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isAlreadyApplied() { return alreadyApplied; }
    public void setAlreadyApplied(boolean alreadyApplied) { this.alreadyApplied = alreadyApplied; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMatchAnalysis() { return matchAnalysis; }
    public void setMatchAnalysis(String matchAnalysis) { this.matchAnalysis = matchAnalysis; }
    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    public CoverLetterStatus getCoverLetterStatus() { return coverLetterStatus; }
    public void setCoverLetterStatus(CoverLetterStatus coverLetterStatus) { this.coverLetterStatus = coverLetterStatus; }
    public boolean isCoverLetterEditedManually() { return coverLetterEditedManually; }
    public void setCoverLetterEditedManually(boolean coverLetterEditedManually) { this.coverLetterEditedManually = coverLetterEditedManually; }
    public String getGenerationProvider() { return generationProvider; }
    public void setGenerationProvider(String generationProvider) { this.generationProvider = generationProvider; }
    public String getGenerationModel() { return generationModel; }
    public void setGenerationModel(String generationModel) { this.generationModel = generationModel; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public int getGenerationAttempts() { return generationAttempts; }
    public void setGenerationAttempts(int generationAttempts) { this.generationAttempts = generationAttempts; }
    public String getGenerationError() { return generationError; }
    public void setGenerationError(String generationError) { this.generationError = generationError; }
    public ApplicationStatus getApplyStatus() { return applyStatus; }
    public void setApplyStatus(ApplicationStatus applyStatus) { this.applyStatus = applyStatus; }
    public String getApplyErrorCode() { return applyErrorCode; }
    public void setApplyErrorCode(String applyErrorCode) { this.applyErrorCode = applyErrorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
