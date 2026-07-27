package com.hhclicker.controller;

import com.hhclicker.dto.request.UpdateResumeProfileRequest;
import com.hhclicker.dto.response.ResumeResponse;
import com.hhclicker.entity.HhAccount;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.HhAccountService;
import com.hhclicker.service.ResumeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumes;
    private final HhAccountService accounts;

    public ResumeController(ResumeService resumes, HhAccountService accounts) {
        this.resumes = resumes;
        this.accounts = accounts;
    }

    @GetMapping
    public List<ResumeResponse> list() {
        return resumes.list(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public ResumeResponse get(@PathVariable UUID id) {
        return resumes.get(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/refresh")
    public ResumeResponse refresh(@PathVariable UUID id, @RequestParam UUID hhAccountId) {
        UUID userId = SecurityUtils.currentUserId();
        HhAccount account = accounts.requireOwned(userId, hhAccountId);
        return resumes.refresh(userId, account, accounts.decryptSession(account), id);
    }

    @PutMapping("/{id}/profile")
    public ResumeResponse updateProfile(@PathVariable UUID id, @RequestBody UpdateResumeProfileRequest request) {
        return resumes.updateProfile(SecurityUtils.currentUserId(), id, request);
    }
}
