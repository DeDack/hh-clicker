package com.hhclicker.service;

import com.hhclicker.config.EncryptionProperties;
import com.hhclicker.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
@EnableConfigurationProperties(EncryptionProperties.class)
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final EncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(EncryptionProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal((plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("ENCRYPTION_ERROR", "Не удалось зашифровать данные");
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_BYTES) {
                throw new BusinessException("ENCRYPTION_ERROR", "Зашифрованные данные повреждены");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("ENCRYPTION_ERROR", "Зашифрованные данные повреждены");
        }
    }

    private SecretKeySpec keySpec() {
        String raw = properties.getHhSessionKey();
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("ENCRYPTION_KEY_MISSING", "Ключ шифрования HH-сессии не настроен");
        }
        byte[] key = decodeKey(raw.strip());
        if (!(key.length == 16 || key.length == 24 || key.length == 32)) {
            throw new BusinessException("ENCRYPTION_KEY_INVALID", "Ключ шифрования HH-сессии должен быть 16, 24 или 32 байта");
        }
        return new SecretKeySpec(key, "AES");
    }

    private byte[] decodeKey(String raw) {
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Treat as raw text key below.
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
