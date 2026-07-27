package com.hhclicker.entity;

import com.hhclicker.enumeration.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_attempts")
public class ApplicationAttempt {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_vacancy_id", nullable = false)
    private CampaignVacancy campaignVacancy;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplicationStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "safe_error_message")
    private String safeErrorMessage;

    @Column(name = "topic_id", length = 120)
    private String topicId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public CampaignVacancy getCampaignVacancy() { return campaignVacancy; }
    public void setCampaignVacancy(CampaignVacancy campaignVacancy) { this.campaignVacancy = campaignVacancy; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getSafeErrorMessage() { return safeErrorMessage; }
    public void setSafeErrorMessage(String safeErrorMessage) { this.safeErrorMessage = safeErrorMessage; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public Instant getCreatedAt() { return createdAt; }
}
