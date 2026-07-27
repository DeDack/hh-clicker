package com.hhclicker.repository;

import com.hhclicker.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {
    List<SavedSearch> findAllByUserId(UUID userId);
    List<SavedSearch> findAllByHhAccountId(UUID hhAccountId);
    Optional<SavedSearch> findByIdAndUserId(UUID id, UUID userId);
    void deleteAllByHhAccountId(UUID hhAccountId);
}
