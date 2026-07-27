package com.hhclicker.service;

import com.hhclicker.entity.User;

public interface CoverLetterPermissionService {
    void checkGenerationAllowed(User user);
    boolean isGenerationAllowed(User user);
}
