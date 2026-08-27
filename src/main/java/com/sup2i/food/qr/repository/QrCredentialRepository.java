package com.sup2i.food.qr.repository;

import com.sup2i.food.qr.domain.QrCredential;
import com.sup2i.food.qr.domain.QrCredentialStatus;
import com.sup2i.food.qr.domain.QrCredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrCredentialRepository
    extends JpaRepository<
        QrCredential,
        UUID
    > {

    Optional<QrCredential>
        findByTokenHash(
            String tokenHash
        );

    boolean
        existsByCredentialTypeAndSubjectIdAndStatus(
            QrCredentialType credentialType,
            UUID subjectId,
            QrCredentialStatus status
        );
}
