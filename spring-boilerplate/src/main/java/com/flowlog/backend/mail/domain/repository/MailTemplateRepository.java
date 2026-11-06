package com.flowlog.backend.mail.domain.repository;

import com.flowlog.backend.mail.domain.model.MailTemplate;
import com.flowlog.backend.mail.domain.model.MailTemplateId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 메일 템플릿 Repository.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public interface MailTemplateRepository extends JpaRepository<MailTemplate, MailTemplateId> {
}
