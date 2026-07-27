package com.hhclicker.service;

import com.hhclicker.dto.request.UpdateResumeProfileRequest;
import com.hhclicker.dto.response.ResumeResponse;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.Resume;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.CandidateGender;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.LoadResumeAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.ResumeSummaryAdapterResponse;
import com.hhclicker.repository.ResumeRepository;
import com.hhclicker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResumeService {
    private final ResumeRepository resumes;
    private final UserRepository users;
    private final HhAdapterClient adapterClient;

    public ResumeService(ResumeRepository resumes, UserRepository users, HhAdapterClient adapterClient) {
        this.resumes = resumes;
        this.users = users;
        this.adapterClient = adapterClient;
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(UUID userId) {
        return resumes.findAllByUserId(userId).stream().map(ResumeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(UUID userId, UUID resumeId) {
        return ResumeResponse.from(requireOwned(userId, resumeId));
    }

    @Transactional
    public List<ResumeResponse> sync(User user, HhAccount account, HhSessionAdapterPayload session) {
        List<ResumeSummaryAdapterResponse> summaries = adapterClient.listResumes(session).resumes();
        for (ResumeSummaryAdapterResponse summary : summaries) {
            refreshOne(user, account, session, summary.hhResumeId(), summary.title());
        }
        return resumes.findAllByHhAccountId(account.getId()).stream().map(ResumeResponse::from).toList();
    }

    @Transactional
    public ResumeResponse refresh(UUID userId, HhAccount account, HhSessionAdapterPayload session, UUID resumeId) {
        Resume resume = requireOwned(userId, resumeId);
        if (!resume.getHhAccount().getId().equals(account.getId())) {
            throw new BusinessException("FORBIDDEN", "Резюме не принадлежит HH-аккаунту");
        }
        return ResumeResponse.from(refreshOne(resume.getUser(), account, session, resume.getHhResumeId(), resume.getTitle()));
    }

    @Transactional
    public ResumeResponse updateProfile(UUID userId, UUID resumeId, UpdateResumeProfileRequest request) {
        Resume resume = requireOwned(userId, resumeId);
        resume.setCandidateProfile(request.candidateProfile());
        resume.setTelegramUsername(normalizeTelegram(request.telegramUsername()));
        return ResumeResponse.from(resumes.save(resume));
    }

    private Resume refreshOne(User user, HhAccount account, HhSessionAdapterPayload session, String hhResumeId, String fallbackTitle) {
        LoadResumeAdapterResponse loaded = adapterClient.loadResume(session, hhResumeId, fallbackTitle);
        Resume resume = resumes.findByHhAccountIdAndHhResumeId(account.getId(), hhResumeId).orElseGet(Resume::new);
        resume.setUser(user);
        resume.setHhAccount(account);
        resume.setHhResumeId(loaded.hhResumeId());
        resume.setTitle(loaded.title());
        resume.setText(loaded.text());
        resume.setContentHash(loaded.contentHash());
        resume.setGender(parseGender(loaded.gender()));
        resume.setActive(true);
        resume.setLastSyncedAt(Instant.now());
        return resumes.save(resume);
    }

    private Resume requireOwned(UUID userId, UUID resumeId) {
        return resumes.findByIdAndUserId(resumeId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Резюме не найдено"));
    }

    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден"));
    }

    private String normalizeTelegram(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (!value.matches("[A-Za-z0-9_]{5,32}")) {
            throw new BusinessException("VALIDATION_ERROR", "Telegram username должен быть без @, 5-32 символа: латиница, цифры или _");
        }
        return value;
    }

    private CandidateGender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return CandidateGender.UNKNOWN;
        }
        try {
            return CandidateGender.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CandidateGender.UNKNOWN;
        }
    }
}
