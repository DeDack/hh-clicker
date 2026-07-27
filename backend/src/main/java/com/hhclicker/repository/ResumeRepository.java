package com.hhclicker.repository;

import com.hhclicker.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    List<Resume> findAllByUserId(UUID userId);
    List<Resume> findAllByHhAccountId(UUID hhAccountId);
    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);
    Optional<Resume> findByHhAccountIdAndHhResumeId(UUID hhAccountId, String hhResumeId);
    boolean existsByIdAndHhAccountIdAndUserId(UUID id, UUID hhAccountId, UUID userId);
    void deleteAllByHhAccountId(UUID hhAccountId);
}
