package com.sup2i.food.security.service;

import com.sup2i.food.security.domain.UserMfaMethod;
import com.sup2i.food.security.domain.UserMfaRecoveryCode;
import com.sup2i.food.security.repository.UserMfaRecoveryCodeRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class MfaRecoveryCodeService {

    private final UserMfaRecoveryCodeRepository repository;
    private final TokenHashService tokenHashService;

    private final SecureRandom secureRandom =
        new SecureRandom();

    public MfaRecoveryCodeService(
        UserMfaRecoveryCodeRepository repository,
        TokenHashService tokenHashService
    ) {
        this.repository = repository;
        this.tokenHashService =
            tokenHashService;
    }

    public List<String> generate(
        UserMfaMethod method,
        int count
    ) {
        List<String> rawCodes =
            new ArrayList<>();

        List<UserMfaRecoveryCode> entities =
            new ArrayList<>();

        for (
            int index = 0;
            index < count;
            index++
        ) {
            byte[] bytes =
                new byte[18];

            secureRandom.nextBytes(bytes);

            String raw =
                "RC-"
                    + Base64
                        .getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);

            String hash =
                tokenHashService.hash(raw);

            rawCodes.add(raw);

            entities.add(
                new UserMfaRecoveryCode(
                    method,
                    hash
                )
            );
        }

        repository.saveAll(entities);

        return List.copyOf(
            rawCodes
        );
    }

    public boolean consume(
        UUID userId,
        UUID methodId,
        String rawCode,
        OffsetDateTime now
    ) {
        if (
            rawCode == null
            || rawCode.isBlank()
        ) {
            return false;
        }

        String hash =
            tokenHashService.hash(
                rawCode.trim()
            );

        UserMfaRecoveryCode recovery =
            repository
                .findForUpdate(
                    hash,
                    methodId,
                    userId
                )
                .orElse(null);

        if (
            recovery == null
            || !recovery.isAvailable()
        ) {
            return false;
        }

        recovery.markUsed(now);

        repository.save(recovery);

        return true;
    }
}