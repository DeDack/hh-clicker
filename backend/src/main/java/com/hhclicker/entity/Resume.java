package com.hhclicker.entity;

import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.CandidateGender;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resumes", uniqueConstraints = @UniqueConstraint(name = "uq_resumes_hh_account_resume", columnNames = {"hh_account_id", "hh_resume_id"}))
public class Resume {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hh_account_id", nullable = false)
    private HhAccount hhAccount;

    @Column(name = "hh_resume_id", nullable = false, length = 96)
    private String hhResumeId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false)
    private String text;

    @Column(name = "candidate_profile")
    private String candidateProfile;

    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CandidateGender gender = CandidateGender.UNKNOWN;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

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
    public String getHhResumeId() { return hhResumeId; }
    public void setHhResumeId(String hhResumeId) { this.hhResumeId = hhResumeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCandidateProfile() { return candidateProfile; }
    public void setCandidateProfile(String candidateProfile) { this.candidateProfile = candidateProfile; }
    public String getTelegramUsername() { return telegramUsername; }
    public void setTelegramUsername(String telegramUsername) { this.telegramUsername = telegramUsername; }
    public CandidateGender getGender() { return gender == null ? CandidateGender.UNKNOWN : gender; }
    public void setGender(CandidateGender gender) { this.gender = gender == null ? CandidateGender.UNKNOWN : gender; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
