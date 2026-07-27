package com.hhclicker.service;

import com.hhclicker.entity.HhAccount;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.HhAccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HhAccountOwnershipService {
    private final HhAccountRepository accounts;

    public HhAccountOwnershipService(HhAccountRepository accounts) {
        this.accounts = accounts;
    }

    public HhAccount requireOwned(UUID hhAccountId, UUID userId) {
        return accounts.findByIdAndUserId(hhAccountId, userId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "HH-аккаунт не найден"));
    }
}
