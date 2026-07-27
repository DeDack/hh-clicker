package com.hhclicker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhclicker.dto.request.CreateHhAccountRequest;
import com.hhclicker.dto.request.RefreshHhSessionRequest;
import com.hhclicker.dto.request.UpdateHhAccountRequest;
import com.hhclicker.dto.response.HhAccountResponse;
import com.hhclicker.dto.response.ResumeResponse;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.HhAccountStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.ParsedCurlAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.SessionValidationAdapterResponse;
import com.hhclicker.repository.HhAccountRepository;
import com.hhclicker.repository.ApplicationAttemptRepository;
import com.hhclicker.repository.ApplicationCampaignRepository;
import com.hhclicker.repository.CampaignVacancyRepository;
import com.hhclicker.repository.ResumeRepository;
import com.hhclicker.repository.SavedSearchRepository;
import com.hhclicker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HhAccountService {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final HhAccountRepository accounts;
    private final UserRepository users;
    private final HhAdapterClient adapterClient;
    private final EncryptionService encryptionService;
    private final ResumeService resumeService;
    private final ApplicationAttemptRepository attempts;
    private final CampaignVacancyRepository vacancies;
    private final ApplicationCampaignRepository campaigns;
    private final SavedSearchRepository savedSearches;
    private final ResumeRepository resumes;
    private final ObjectMapper objectMapper;

    public HhAccountService(
        HhAccountRepository accounts,
        UserRepository users,
        HhAdapterClient adapterClient,
        EncryptionService encryptionService,
        ResumeService resumeService,
        ApplicationAttemptRepository attempts,
        CampaignVacancyRepository vacancies,
        ApplicationCampaignRepository campaigns,
        SavedSearchRepository savedSearches,
        ResumeRepository resumes,
        ObjectMapper objectMapper
    ) {
        this.accounts = accounts;
        this.users = users;
        this.adapterClient = adapterClient;
        this.encryptionService = encryptionService;
        this.resumeService = resumeService;
        this.attempts = attempts;
        this.vacancies = vacancies;
        this.campaigns = campaigns;
        this.savedSearches = savedSearches;
        this.resumes = resumes;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<HhAccountResponse> list(UUID userId) {
        return accounts.findAllByUserId(userId).stream().map(HhAccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public HhAccountResponse get(UUID userId, UUID accountId) {
        return HhAccountResponse.from(requireOwned(userId, accountId));
    }

    @Transactional
    public HhAccountResponse create(UUID userId, CreateHhAccountRequest request) {
        User user = requireUser(userId);
        accounts.findByUserIdAndName(userId, request.name().strip()).ifPresent(existing -> {
            throw new BusinessException("CONFLICT", "HH-аккаунт с таким именем уже существует");
        });
        ParsedCurlAdapterResponse parsed = adapterClient.parseCurl(request.rawCurl());
        HhSessionAdapterPayload session = new HhSessionAdapterPayload(parsed.cookies(), parsed.headers());
        SessionValidationAdapterResponse validation = adapterClient.validateSession(session);
        if (!validation.valid()) {
            throw new BusinessException("HH_SESSION_INVALID", validation.message());
        }
        HhAccount account = new HhAccount();
        account.setUser(user);
        account.setName(request.name().strip());
        account.setHhHost("https://hh.ru");
        account.setEncryptedCookies(encrypt(parsed.cookies()));
        account.setEncryptedHeaders(encrypt(parsed.headers()));
        account.setStatus(HhAccountStatus.ACTIVE);
        account.setLastCheckedAt(Instant.now());
        account = accounts.save(account);
        resumeService.sync(user, account, session);
        return HhAccountResponse.from(account);
    }

    @Transactional
    public HhAccountResponse update(UUID userId, UUID accountId, UpdateHhAccountRequest request) {
        HhAccount account = requireOwned(userId, accountId);
        accounts.findByUserIdAndName(userId, request.name().strip()).ifPresent(existing -> {
            if (!existing.getId().equals(accountId)) {
                throw new BusinessException("CONFLICT", "HH-аккаунт с таким именем уже существует");
            }
        });
        account.setName(request.name().strip());
        return HhAccountResponse.from(accounts.save(account));
    }

    @Transactional
    public void delete(UUID userId, UUID accountId) {
        HhAccount account = requireOwned(userId, accountId);
        attempts.deleteAllByHhAccountId(account.getId());
        vacancies.deleteAllByHhAccountId(account.getId());
        campaigns.deleteAllByHhAccountId(account.getId());
        savedSearches.deleteAllByHhAccountId(account.getId());
        resumes.deleteAllByHhAccountId(account.getId());
        accounts.delete(account);
    }

    @Transactional
    public HhAccountResponse check(UUID userId, UUID accountId) {
        HhAccount account = requireOwned(userId, accountId);
        SessionValidationAdapterResponse validation = adapterClient.validateSession(decryptSession(account));
        account.setStatus(validation.valid() ? HhAccountStatus.ACTIVE : HhAccountStatus.INVALID);
        account.setLastCheckedAt(Instant.now());
        return HhAccountResponse.from(accounts.save(account));
    }

    @Transactional
    public HhAccountResponse refreshSession(UUID userId, UUID accountId, RefreshHhSessionRequest request) {
        HhAccount account = requireOwned(userId, accountId);
        ParsedCurlAdapterResponse parsed = adapterClient.parseCurl(request.rawCurl());
        HhSessionAdapterPayload session = new HhSessionAdapterPayload(parsed.cookies(), parsed.headers());
        SessionValidationAdapterResponse validation = adapterClient.validateSession(session);
        if (!validation.valid()) {
            account.setStatus(HhAccountStatus.INVALID);
            account.setLastCheckedAt(Instant.now());
            accounts.save(account);
            throw new BusinessException("HH_SESSION_INVALID", validation.message());
        }
        account.setEncryptedCookies(encrypt(parsed.cookies()));
        account.setEncryptedHeaders(encrypt(parsed.headers()));
        account.setStatus(HhAccountStatus.ACTIVE);
        account.setLastCheckedAt(Instant.now());
        HhAccount saved = accounts.save(account);
        resumeService.sync(saved.getUser(), saved, session);
        return HhAccountResponse.from(saved);
    }

    @Transactional
    public List<ResumeResponse> syncResumes(UUID userId, UUID accountId) {
        HhAccount account = requireOwned(userId, accountId);
        return resumeService.sync(account.getUser(), account, decryptSession(account));
    }

    public HhAccount requireOwned(UUID userId, UUID accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "HH-аккаунт не найден"));
    }

    public HhSessionAdapterPayload decryptSession(HhAccount account) {
        try {
            return new HhSessionAdapterPayload(
                objectMapper.readValue(encryptionService.decrypt(account.getEncryptedCookies()), STRING_MAP),
                objectMapper.readValue(encryptionService.decrypt(account.getEncryptedHeaders()), STRING_MAP)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("ENCRYPTION_ERROR", "Не удалось расшифровать HH-сессию");
        }
    }

    private String encrypt(Map<String, String> value) {
        try {
            return encryptionService.encrypt(objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            throw new BusinessException("ENCRYPTION_ERROR", "Не удалось зашифровать HH-сессию");
        }
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден"));
    }
}
