package com.hhclicker.dto.response;

import com.hhclicker.entity.HhAccount;

import java.time.Instant;
import java.util.UUID;

public record HhAccountResponse(
    UUID id,
    String name,
    String hhHost,
    String status,
    Instant lastCheckedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static HhAccountResponse from(HhAccount account) {
        return new HhAccountResponse(
            account.getId(),
            account.getName(),
            account.getHhHost(),
            account.getStatus().name(),
            account.getLastCheckedAt(),
            account.getCreatedAt(),
            account.getUpdatedAt()
        );
    }
}
