package com.hhclicker.service;

import com.hhclicker.exception.BusinessException;
import com.hhclicker.entity.User;
import org.springframework.stereotype.Service;

@Service
public class DefaultCoverLetterPermissionService implements CoverLetterPermissionService {
    @Override
    public void checkGenerationAllowed(User user) {
        if (!isGenerationAllowed(user)) {
            throw new BusinessException(
                "COVER_LETTER_GENERATION_DISABLED",
                "Генерация сопроводительных писем недоступна для этого аккаунта"
            );
        }
    }

    @Override
    public boolean isGenerationAllowed(User user) {
        return user != null && user.isCoverLetterGenerationEnabled();
    }
}
