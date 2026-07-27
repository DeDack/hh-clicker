package com.hhclicker.repository;

import com.hhclicker.entity.HhAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HhAccountRepository extends JpaRepository<HhAccount, UUID> {
    List<HhAccount> findAllByUserId(UUID userId);
    Optional<HhAccount> findByIdAndUserId(UUID id, UUID userId);
    Optional<HhAccount> findByUserIdAndName(UUID userId, String name);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
