package com.hhclicker.entity;

import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.Resume;
import com.hhclicker.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_searches")
public class SavedSearch {
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

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "search_url", nullable = false)
    private String searchUrl;

    @Column(nullable = false)
    private int pages;

    @Column(name = "vacancy_load_limit")
    private Integer vacancyLoadLimit;

    @Column(name = "include_keywords")
    private String includeKeywords;

    @Column(name = "exclude_keywords")
    private String excludeKeywords;

    @Column(name = "default_cover_letter_mode", nullable = false, length = 32)
    private String defaultCoverLetterMode;

    @Column(name = "default_common_cover_letter")
    private String defaultCommonCoverLetter;

    @Column(name = "default_delay_seconds", nullable = false)
    private BigDecimal defaultDelaySeconds;

    @Column(name = "default_max_applications", nullable = false)
    private int defaultMaxApplications;

    @Column(nullable = false)
    private boolean active = true;

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
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public HhAccount getHhAccount() { return hhAccount; }
    public void setHhAccount(HhAccount hhAccount) { this.hhAccount = hhAccount; }
    public Resume getResume() { return resume; }
    public void setResume(Resume resume) { this.resume = resume; }
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
    public String getDefaultCoverLetterMode() { return defaultCoverLetterMode; }
    public void setDefaultCoverLetterMode(String defaultCoverLetterMode) { this.defaultCoverLetterMode = defaultCoverLetterMode; }
    public String getDefaultCommonCoverLetter() { return defaultCommonCoverLetter; }
    public void setDefaultCommonCoverLetter(String defaultCommonCoverLetter) { this.defaultCommonCoverLetter = defaultCommonCoverLetter; }
    public BigDecimal getDefaultDelaySeconds() { return defaultDelaySeconds; }
    public void setDefaultDelaySeconds(BigDecimal defaultDelaySeconds) { this.defaultDelaySeconds = defaultDelaySeconds; }
    public int getDefaultMaxApplications() { return defaultMaxApplications; }
    public void setDefaultMaxApplications(int defaultMaxApplications) { this.defaultMaxApplications = defaultMaxApplications; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
