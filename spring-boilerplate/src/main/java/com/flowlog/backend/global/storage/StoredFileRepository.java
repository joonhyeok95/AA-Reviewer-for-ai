package com.flowlog.backend.global.storage;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * DB 저장 파일 Repository.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public interface StoredFileRepository extends JpaRepository<StoredFile, String> {
}
