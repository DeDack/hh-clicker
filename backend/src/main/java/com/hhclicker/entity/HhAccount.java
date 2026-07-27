package com.hhclicker.entity;

import com.hhclicker.enumeration.HhAccountStatus;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hh_accounts", uniqueConstraints = @UniqueConstraint(name = "uq_hh_accounts_user_name", columnNames = {"user_id", "name"}))
public class HhAccount {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "encrypted_cookies", nullable = false)
    private String encryptedCookies;

    @Column(name = "encrypted_headers", nullable = false)
    private String encryptedHeaders;

    @Column(name = "hh_host", nullable = false, length = 160)
    private String hhHost = "https://hh.ru";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HhAccountStatus status = HhAccountStatus.UNKNOWN;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

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
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEncryptedCookies() { return encryptedCookies; }
    public void setEncryptedCookies(String encryptedCookies) { this.encryptedCookies = encryptedCookies; }
    public String getEncryptedHeaders() { return encryptedHeaders; }
    public void setEncryptedHeaders(String encryptedHeaders) { this.encryptedHeaders = encryptedHeaders; }
    public String getHhHost() { return hhHost; }
    public void setHhHost(String hhHost) { this.hhHost = hhHost; }
    public HhAccountStatus getStatus() { return status; }
    public void setStatus(HhAccountStatus status) { this.status = status; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
