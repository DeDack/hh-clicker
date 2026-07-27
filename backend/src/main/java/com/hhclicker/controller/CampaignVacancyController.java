package com.hhclicker.controller;

import com.hhclicker.dto.request.UpdateCampaignVacancyRequest;
import com.hhclicker.dto.request.UpdateCoverLetterRequest;
import com.hhclicker.dto.response.CampaignVacancyResponse;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.CampaignService;
import com.hhclicker.service.CoverLetterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/vacancies")
public class CampaignVacancyController {
    private final CampaignService service;
    private final CoverLetterService coverLetters;

    public CampaignVacancyController(CampaignService service, CoverLetterService coverLetters) {
        this.service = service;
        this.coverLetters = coverLetters;
    }

    @GetMapping
    public List<CampaignVacancyResponse> list(@PathVariable UUID campaignId) {
        return service.listVacancies(SecurityUtils.currentUserId(), campaignId);
    }

    @PutMapping("/{vacancyId}")
    public CampaignVacancyResponse update(
        @PathVariable UUID campaignId,
        @PathVariable UUID vacancyId,
        @RequestBody UpdateCampaignVacancyRequest request
    ) {
        return service.updateVacancy(SecurityUtils.currentUserId(), campaignId, vacancyId, request);
    }

    @PostMapping("/profile-mismatches/exclude")
    public List<CampaignVacancyResponse> excludeProfileMismatches(@PathVariable UUID campaignId) {
        return service.excludeProfileMismatches(SecurityUtils.currentUserId(), campaignId);
    }

    @PutMapping("/{vacancyId}/cover-letter")
    public CampaignVacancyResponse updateCoverLetter(
        @PathVariable UUID campaignId,
        @PathVariable UUID vacancyId,
        @RequestBody UpdateCoverLetterRequest request
    ) {
        return coverLetters.updateManual(SecurityUtils.currentUserId(), campaignId, vacancyId, request);
    }

    @PostMapping("/{vacancyId}/cover-letter/regenerate")
    public CampaignVacancyResponse regenerate(
        @PathVariable UUID campaignId,
        @PathVariable UUID vacancyId
    ) {
        return coverLetters.regenerate(SecurityUtils.currentUserId(), campaignId, vacancyId);
    }
}
