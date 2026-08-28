package com.sup2i.food.qr.service;

import com.sup2i.food.qr.domain.QrCredential;
import com.sup2i.food.qr.domain.QrCredentialStatus;
import com.sup2i.food.qr.domain.QrCredentialType;
import com.sup2i.food.qr.exception.QrAlreadyUsedException;
import com.sup2i.food.qr.exception.QrConflictException;
import com.sup2i.food.qr.exception.QrExpiredException;
import com.sup2i.food.qr.exception.QrNotFoundException;
import com.sup2i.food.qr.exception.QrRevokedException;
import com.sup2i.food.qr.repository.QrCredentialRepository;
import com.sup2i.food.security.service.TokenHashService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class QrCredentialService {

    private final QrCredentialRepository qrCredentialRepository;
    private final TokenHashService tokenHashService;

    public QrCredentialService(
        QrCredentialRepository qrCredentialRepository,
        TokenHashService tokenHashService
    ) {
        this.qrCredentialRepository =
            qrCredentialRepository;

        this.tokenHashService =
            tokenHashService;
    }

    @Transactional
    public String issue(
        QrCredentialType credentialType,
        UUID subjectId,
        OffsetDateTime expiresAt
    ) {

        return issueDetailed(
            credentialType,
            subjectId,
            expiresAt
        ).rawToken();
    }

    @Transactional
    public IssuedCredential issueDetailed(
        QrCredentialType credentialType,
        UUID subjectId,
        OffsetDateTime expiresAt
    ) {

        TokenHashService.GeneratedToken generated =
            tokenHashService.generate();

        QrCredential credential =
            new QrCredential(
                credentialType,
                subjectId,
                generated.tokenHash(),
                expiresAt
            );

        try {

            qrCredentialRepository
                .saveAndFlush(credential);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new QrConflictException(
                "A credential already exists for this subject."
            );
        }

        return new IssuedCredential(
            credential.getId(),
            generated.rawToken()
        );
    }

    @Transactional
    public ResolvedCredential resolve(
        String rawToken
    ) {

        String tokenHash =
            tokenHashService.hash(
                rawToken
            );

        QrCredential credential =
            qrCredentialRepository
                .findByTokenHash(
                    tokenHash
                )
                .orElseThrow(() ->
                    new QrNotFoundException(
                        "QR code is invalid."
                    )
                );

        if (
            credential.getStatus()
                == QrCredentialStatus.REVOKED
        ) {
            throw new QrRevokedException(
                "QR code has been revoked."
            );
        }

        if (
            credential.getStatus()
                == QrCredentialStatus.USED
        ) {
            throw new QrAlreadyUsedException(
                "QR code has already been used."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            credential.getStatus()
                == QrCredentialStatus.EXPIRED
            || credential.isExpired(now)
        ) {
            throw new QrExpiredException(
                "QR code has expired."
            );
        }

        credential.markUsed(now);

        qrCredentialRepository
            .saveAndFlush(credential);

        return new ResolvedCredential(
            credential.getCredentialType(),
            credential.getSubjectId()
        );
    }

    @Transactional
    public void revoke(
        UUID credentialId,
        OffsetDateTime at
    ) {

        QrCredential credential =
            qrCredentialRepository
                .findById(credentialId)
                .orElseThrow(() ->
                    new QrNotFoundException(
                        "Credential does not exist."
                    )
                );

        credential.revoke(at);

        qrCredentialRepository
            .saveAndFlush(credential);
    }

    public record ResolvedCredential(
        QrCredentialType credentialType,
        UUID subjectId
    ) {
    }

    public record IssuedCredential(
        UUID credentialId,
        String rawToken
    ) {
    }
}
