package com.hhclicker.service;

import com.hhclicker.dto.request.UpdateUserFeaturesRequest;
import com.hhclicker.dto.request.UpdateUserStatusRequest;
import com.hhclicker.dto.response.UserResponse;
import com.hhclicker.entity.AuditEvent;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.AuditEventType;
import com.hhclicker.enumeration.UserStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.AuditEventRepository;
import com.hhclicker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final AuditEventRepository auditEvents;

    public AdminUserService(UserRepository users, AuditEventRepository auditEvents) {
        this.users = users;
        this.auditEvents = auditEvents;
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse get(UUID id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse updateFeatures(UUID actorUserId, UUID targetUserId, UpdateUserFeaturesRequest request) {
        User target = findUser(targetUserId);
        boolean previous = target.isCoverLetterGenerationEnabled();
        target.setCoverLetterGenerationEnabled(request.coverLetterGenerationEnabled());
        User saved = users.save(target);
        if (previous != request.coverLetterGenerationEnabled()) {
            audit(
                actorUserId,
                target,
                request.coverLetterGenerationEnabled()
                    ? AuditEventType.USER_COVER_LETTER_GENERATION_ENABLED
                    : AuditEventType.USER_COVER_LETTER_GENERATION_DISABLED,
                String.valueOf(previous),
                String.valueOf(request.coverLetterGenerationEnabled())
            );
        }
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateStatus(UUID actorUserId, UUID targetUserId, UpdateUserStatusRequest request) {
        User target = findUser(targetUserId);
        UserStatus next = request.status() == null ? UserStatus.ACTIVE : request.status();
        UserStatus previous = target.getStatus();
        target.setStatus(next);
        User saved = users.save(target);
        if (previous != next) {
            audit(actorUserId, target, next == UserStatus.BLOCKED ? AuditEventType.USER_BLOCKED : AuditEventType.USER_UNBLOCKED, previous.name(), next.name());
        }
        return UserResponse.from(saved);
    }

    private User findUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Пользователь не найден"));
    }

    private void audit(UUID actorUserId, User target, AuditEventType type, String oldValue, String newValue) {
        AuditEvent event = new AuditEvent();
        event.setUser(target);
        event.setEventType(type);
        event.setDetails("""
            {"actorUserId":"%s","targetUserId":"%s","oldValue":"%s","newValue":"%s"}
            """.formatted(actorUserId, target.getId(), oldValue, newValue).trim());
        auditEvents.save(event);
    }
}
