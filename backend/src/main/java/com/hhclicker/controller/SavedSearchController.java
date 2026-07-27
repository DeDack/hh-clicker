package com.hhclicker.controller;

import com.hhclicker.dto.request.CreateSavedSearchRequest;
import com.hhclicker.dto.request.UpdateSavedSearchRequest;
import com.hhclicker.dto.response.SavedSearchResponse;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.SavedSearchService;
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
@RequestMapping("/api/saved-searches")
public class SavedSearchController {
    private final SavedSearchService service;

    public SavedSearchController(SavedSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedSearchResponse> list() {
        return service.list(SecurityUtils.currentUserId());
    }

    @PostMapping
    public SavedSearchResponse create(@Valid @RequestBody CreateSavedSearchRequest request) {
        return service.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping("/{id}")
    public SavedSearchResponse get(@PathVariable UUID id) {
        return service.get(SecurityUtils.currentUserId(), id);
    }

    @PutMapping("/{id}")
    public SavedSearchResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSavedSearchRequest request) {
        return service.update(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
