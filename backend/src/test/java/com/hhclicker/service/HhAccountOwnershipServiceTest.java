package com.hhclicker.service;

import com.hhclicker.entity.HhAccount;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.HhAccountRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HhAccountOwnershipServiceTest {
    @Test
    void returnsOwnedAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        HhAccount account = new HhAccount();
        HhAccountRepository repository = mock(HhAccountRepository.class);
        when(repository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        HhAccountOwnershipService service = new HhAccountOwnershipService(repository);

        assertThat(service.requireOwned(accountId, userId)).isSameAs(account);
    }

    @Test
    void rejectsForeignAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        HhAccountRepository repository = mock(HhAccountRepository.class);
        when(repository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        HhAccountOwnershipService service = new HhAccountOwnershipService(repository);

        assertThatThrownBy(() -> service.requireOwned(accountId, userId))
            .isInstanceOf(BusinessException.class)
            .hasMessage("HH-аккаунт не найден");
    }
}
