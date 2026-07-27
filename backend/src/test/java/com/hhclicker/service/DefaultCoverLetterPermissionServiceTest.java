package com.hhclicker.service;

import com.hhclicker.entity.User;
import com.hhclicker.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCoverLetterPermissionServiceTest {
    private final DefaultCoverLetterPermissionService service = new DefaultCoverLetterPermissionService();

    @Test
    void disabledUserCannotGenerate() {
        User user = new User();
        user.setCoverLetterGenerationEnabled(false);

        assertThat(service.isGenerationAllowed(user)).isFalse();
        assertThatThrownBy(() -> service.checkGenerationAllowed(user))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Генерация сопроводительных писем недоступна для этого аккаунта");
    }

    @Test
    void enabledUserCanGenerate() {
        User user = new User();
        user.setCoverLetterGenerationEnabled(true);

        assertThat(service.isGenerationAllowed(user)).isTrue();
        service.checkGenerationAllowed(user);
    }
}
