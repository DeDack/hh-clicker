package com.hhclicker.controller;

import com.hhclicker.dto.request.CreateCampaignRequest;
import com.hhclicker.dto.request.UpdateCampaignSettingsRequest;
import com.hhclicker.dto.response.CampaignDetailsResponse;
import com.hhclicker.dto.response.CampaignResponse;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.ApplicationService;
import com.hhclicker.service.CampaignService;
import com.hhclicker.service.CoverLetterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/campaigns")
public class CampaignController {
    private final CampaignService service;
    private final CoverLetterService coverLetters;
    private final ApplicationService applications;

    public CampaignController(CampaignService service, CoverLetterService coverLetters, ApplicationService applications) {
        this.service = service;
        this.coverLetters = coverLetters;
        this.applications = applications;
    }

    @GetMapping
    public List<CampaignResponse> list() {
        return service.list(SecurityUtils.currentUserId());
    }

    @PostMapping
    public CampaignResponse create(@Valid @RequestBody CreateCampaignRequest request) {
        return service.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping("/{id}")
    public CampaignDetailsResponse get(@PathVariable UUID id) {
        return service.get(SecurityUtils.currentUserId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/settings")
    public CampaignResponse updateSettings(@PathVariable UUID id, @Valid @RequestBody UpdateCampaignSettingsRequest request) {
        return service.updateSettings(SecurityUtils.currentUserId(), id, request);
    }

    @PostMapping("/{id}/preview")
    public CampaignResponse preview(@PathVariable UUID id) {
        return service.startPreview(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/vacancies/reload")
    public CampaignResponse reloadPreview(@PathVariable UUID id) {
        return service.reloadPreview(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/cover-letters/generate")
    public CampaignResponse generateCoverLetters(@PathVariable UUID id) {
        return coverLetters.startMassGeneration(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/apply")
    public CampaignResponse apply(@PathVariable UUID id) {
        return applications.start(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/stop")
    public CampaignResponse stop(@PathVariable UUID id) {
        return applications.stop(SecurityUtils.currentUserId(), id);
    }

    @GetMapping("/{id}/state")
    public CampaignDetailsResponse state(@PathVariable UUID id) {
        return service.get(SecurityUtils.currentUserId(), id);
    }
}
