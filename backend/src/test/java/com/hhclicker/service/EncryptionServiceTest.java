package com.hhclicker.service;

import com.hhclicker.config.EncryptionProperties;
import com.hhclicker.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {
    @Test
    void encryptsAndDecrypts() {
        EncryptionService service = service("12345678901234567890123456789012");

        String encrypted = service.encrypt("{\"hhtoken\":\"secret\"}");

        assertThat(encrypted).doesNotContain("secret");
        assertThat(service.decrypt(encrypted)).isEqualTo("{\"hhtoken\":\"secret\"}");
    }

    @Test
    void usesDifferentIv() {
        EncryptionService service = service("12345678901234567890123456789012");

        assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
    }

    @Test
    void rejectsDamagedCiphertext() {
        EncryptionService service = service("12345678901234567890123456789012");

        assertThatThrownBy(() -> service.decrypt("broken"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMissingKey() {
        EncryptionService service = service("");

        assertThatThrownBy(() -> service.encrypt("data"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Ключ шифрования HH-сессии не настроен");
    }

    private EncryptionService service(String key) {
        EncryptionProperties properties = new EncryptionProperties();
        properties.setHhSessionKey(key);
        return new EncryptionService(properties);
    }
}
