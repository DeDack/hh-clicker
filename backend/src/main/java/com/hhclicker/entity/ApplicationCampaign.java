package com.hhclicker.entity;

import com.hhclicker.enumeration.CampaignStatus;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class ApplicationCampaign {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hh_account_id", nullable = false)
    private HhAccount hhAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_search_id")
    private SavedSearch savedSearch;

    @Column(nullable = false, length = 220)
    private String name;

    @Column(name = "search_url", nullable = false)
    private String searchUrl;

    @Column(nullable = false)
    private int pages = 1;

    @Column(name = "vacancy_load_limit")
    private Integer vacancyLoadLimit;

    @Column(name = "include_keywords")
    private String includeKeywords;

    @Column(name = "exclude_keywords")
    private String excludeKeywords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "cover_letter_mode", nullable = false, length = 32)
    private String coverLetterMode;

    @Column(name = "common_cover_letter")
    private String commonCoverLetter;

    @Column(name = "review_cover_letters_before_apply", nullable = false)
    private boolean reviewCoverLettersBeforeApply = true;

    @Column(name = "delay_seconds", nullable = false)
    private BigDecimal delaySeconds;

    @Column(name = "max_applications", nullable = false)
    private int maxApplications;

    @Column(name = "stop_requested", nullable = false)
    private boolean stopRequested;

    @Column(name = "total_vacancies", nullable = false)
    private int totalVacancies;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "already_count", nullable = false)
    private int alreadyCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

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
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public HhAccount getHhAccount() { return hhAccount; }
    public void setHhAccount(HhAccount hhAccount) { this.hhAccount = hhAccount; }
    public Resume getResume() { return resume; }
    public void setResume(Resume resume) { this.resume = resume; }
    public SavedSearch getSavedSearch() { return savedSearch; }
    public void setSavedSearch(SavedSearch savedSearch) { this.savedSearch = savedSearch; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSearchUrl() { return searchUrl; }
    public void setSearchUrl(String searchUrl) { this.searchUrl = searchUrl; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public Integer getVacancyLoadLimit() { return vacancyLoadLimit; }
    public void setVacancyLoadLimit(Integer vacancyLoadLimit) { this.vacancyLoadLimit = vacancyLoadLimit; }
    public String getIncludeKeywords() { return includeKeywords; }
    public void setIncludeKeywords(String includeKeywords) { this.includeKeywords = includeKeywords; }
    public String getExcludeKeywords() { return excludeKeywords; }
    public void setExcludeKeywords(String excludeKeywords) { this.excludeKeywords = excludeKeywords; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public String getCoverLetterMode() { return coverLetterMode; }
    public void setCoverLetterMode(String coverLetterMode) { this.coverLetterMode = coverLetterMode; }
    public String getCommonCoverLetter() { return commonCoverLetter; }
    public void setCommonCoverLetter(String commonCoverLetter) { this.commonCoverLetter = commonCoverLetter; }
    public boolean isReviewCoverLettersBeforeApply() { return reviewCoverLettersBeforeApply; }
    public void setReviewCoverLettersBeforeApply(boolean reviewCoverLettersBeforeApply) { this.reviewCoverLettersBeforeApply = reviewCoverLettersBeforeApply; }
    public BigDecimal getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(BigDecimal delaySeconds) { this.delaySeconds = delaySeconds; }
    public int getMaxApplications() { return maxApplications; }
    public void setMaxApplications(int maxApplications) { this.maxApplications = maxApplications; }
    public boolean isStopRequested() { return stopRequested; }
    public void setStopRequested(boolean stopRequested) { this.stopRequested = stopRequested; }
    public int getTotalVacancies() { return totalVacancies; }
    public void setTotalVacancies(int totalVacancies) { this.totalVacancies = totalVacancies; }
    public int getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(int generatedCount) { this.generatedCount = generatedCount; }
    public int getAppliedCount() { return appliedCount; }
    public void setAppliedCount(int appliedCount) { this.appliedCount = appliedCount; }
    public int getAlreadyCount() { return alreadyCount; }
    public void setAlreadyCount(int alreadyCount) { this.alreadyCount = alreadyCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
