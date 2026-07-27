package com.hhclicker.controller;

import com.hhclicker.dto.request.UpdateUserFeaturesRequest;
import com.hhclicker.dto.request.UpdateUserStatusRequest;
import com.hhclicker.dto.response.UserResponse;
import com.hhclicker.security.SecurityUtils;
import com.hhclicker.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/features")
    public UserResponse updateFeatures(@PathVariable UUID id, @RequestBody UpdateUserFeaturesRequest request) {
        return service.updateFeatures(SecurityUtils.currentUserId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @RequestBody UpdateUserStatusRequest request) {
        return service.updateStatus(SecurityUtils.currentUserId(), id, request);
    }
}
