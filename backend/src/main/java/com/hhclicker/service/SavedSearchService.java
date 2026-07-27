package com.hhclicker.service;

import com.hhclicker.dto.request.CreateSavedSearchRequest;
import com.hhclicker.dto.request.UpdateSavedSearchRequest;
import com.hhclicker.dto.response.SavedSearchResponse;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.entity.Resume;
import com.hhclicker.entity.SavedSearch;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.ResumeRepository;
import com.hhclicker.repository.SavedSearchRepository;
import com.hhclicker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class SavedSearchService {
    private final SavedSearchRepository searches;
    private final ResumeRepository resumes;
    private final UserRepository users;
    private final HhAccountService accounts;

    public SavedSearchService(SavedSearchRepository searches, ResumeRepository resumes, UserRepository users, HhAccountService accounts) {
        this.searches = searches;
        this.resumes = resumes;
        this.users = users;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID userId) {
        return searches.findAllByUserId(userId).stream().map(SavedSearchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SavedSearchResponse get(UUID userId, UUID id) {
        return SavedSearchResponse.from(requireOwned(userId, id));
    }

    @Transactional
    public SavedSearchResponse create(UUID userId, CreateSavedSearchRequest request) {
        SavedSearch search = new SavedSearch();
        HhAccount account = accounts.requireOwned(userId, request.hhAccountId());
        Resume resume = requireResume(userId, account.getId(), request.resumeId());
        search.setUser(users.findById(userId).orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден")));
        fill(search, account, resume, request.name(), request.searchUrl(), request.pages(), request.includeKeywords(), request.excludeKeywords(),
            request.vacancyLoadLimit(), request.defaultCoverLetterMode(), request.defaultCommonCoverLetter(), request.defaultDelaySeconds(), request.defaultMaxApplications(), true);
        return SavedSearchResponse.from(searches.save(search));
    }

    @Transactional
    public SavedSearchResponse update(UUID userId, UUID id, UpdateSavedSearchRequest request) {
        SavedSearch search = requireOwned(userId, id);
        HhAccount account = accounts.requireOwned(userId, request.hhAccountId());
        Resume resume = requireResume(userId, account.getId(), request.resumeId());
        fill(search, account, resume, request.name(), request.searchUrl(), request.pages(), request.includeKeywords(), request.excludeKeywords(),
            request.vacancyLoadLimit(), request.defaultCoverLetterMode(), request.defaultCommonCoverLetter(), request.defaultDelaySeconds(), request.defaultMaxApplications(), request.active());
        return SavedSearchResponse.from(searches.save(search));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        searches.delete(requireOwned(userId, id));
    }

    private void fill(
        SavedSearch search,
        HhAccount account,
        Resume resume,
        String name,
        String searchUrl,
        int pages,
        String includeKeywords,
        String excludeKeywords,
        Integer vacancyLoadLimit,
        String coverLetterMode,
        String commonCoverLetter,
        BigDecimal delaySeconds,
        int maxApplications,
        boolean active
    ) {
        validateSearchUrl(searchUrl);
        if (delaySeconds != null && delaySeconds.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("VALIDATION_ERROR", "Задержка не может быть отрицательной");
        }
        search.setHhAccount(account);
        search.setResume(resume);
        search.setName(name.strip());
        search.setSearchUrl(searchUrl.strip());
        search.setPages(Math.max(1, Math.min(pages, 50)));
        search.setVacancyLoadLimit(normalizeLimit(vacancyLoadLimit));
        search.setIncludeKeywords(blankToNull(includeKeywords));
        search.setExcludeKeywords(blankToNull(excludeKeywords));
        search.setDefaultCoverLetterMode(normalizeCoverLetterMode(coverLetterMode));
        search.setDefaultCommonCoverLetter(blankToNull(commonCoverLetter));
        search.setDefaultDelaySeconds(delaySeconds == null ? BigDecimal.ONE : delaySeconds);
        search.setDefaultMaxApplications(Math.max(0, maxApplications));
        search.setActive(active);
    }

    @Transactional(readOnly = true)
    private SavedSearch requireOwned(UUID userId, UUID id) {
        return searches.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Сохранённый поиск не найден"));
    }

    private Resume requireResume(UUID userId, UUID hhAccountId, UUID resumeId) {
        return resumes.findByIdAndUserId(resumeId, userId)
            .filter(resume -> resume.getHhAccount().getId().equals(hhAccountId))
            .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Резюме должно принадлежать выбранному HH-аккаунту"));
    }

    private void validateSearchUrl(String raw) {
        try {
            URI uri = URI.create(raw.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!"https".equals(uri.getScheme()) || !("hh.ru".equals(host) || host.endsWith(".hh.ru")) || !"/search/vacancy".equals(uri.getPath())) {
                throw new BusinessException("VALIDATION_ERROR", "URL поиска должен вести на https://hh.ru/search/vacancy");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Некорректный URL поиска");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Integer normalizeLimit(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return Math.min(value, 10_000);
    }

    private String normalizeCoverLetterMode(String raw) {
        String mode = blankToNull(raw);
        if (mode == null) {
            return "PERSONAL_AI";
        }
        return switch (mode.strip().toUpperCase()) {
            case "PERSONAL", "PERSONAL_AI" -> "PERSONAL_AI";
            case "COMMON" -> "COMMON";
            case "NONE" -> "NONE";
            default -> throw new BusinessException("VALIDATION_ERROR", "Режим письма должен быть NONE, COMMON или PERSONAL_AI");
        };
    }
}
