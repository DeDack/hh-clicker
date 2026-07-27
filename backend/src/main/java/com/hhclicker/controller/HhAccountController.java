package com.hhclicker.controller;

import com.hhclicker.dto.request.CreateHhAccountRequest;
import com.hhclicker.dto.request.RefreshHhSessionRequest;
import com.hhclicker.dto.request.UpdateHhAccountRequest;
import com.hhclicker.dto.response.HhAccountResponse;
import com.hhclicker.dto.response.ResumeResponse;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.HhAccountService;
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
@RequestMapping("/api/hh-accounts")
public class HhAccountController {
    private final HhAccountService service;

    public HhAccountController(HhAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<HhAccountResponse> list() {
        return service.list(SecurityUtils.currentUserId());
    }

    @PostMapping
    public HhAccountResponse create(@Valid @RequestBody CreateHhAccountRequest request) {
        return service.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping("/{id}")
    public HhAccountResponse get(@PathVariable UUID id) {
        return service.get(SecurityUtils.currentUserId(), id);
    }

    @PutMapping("/{id}")
    public HhAccountResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateHhAccountRequest request) {
        return service.update(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check")
    public HhAccountResponse check(@PathVariable UUID id) {
        return service.check(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/{id}/refresh-session")
    public HhAccountResponse refreshSession(@PathVariable UUID id, @Valid @RequestBody RefreshHhSessionRequest request) {
        return service.refreshSession(SecurityUtils.currentUserId(), id, request);
    }

    @PostMapping("/{id}/resumes/sync")
    public List<ResumeResponse> syncResumes(@PathVariable UUID id) {
        return service.syncResumes(SecurityUtils.currentUserId(), id);
    }
}
